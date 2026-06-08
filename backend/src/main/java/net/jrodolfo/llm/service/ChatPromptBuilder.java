package net.jrodolfo.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.model.ChatSessionMessage;
import net.jrodolfo.llm.provider.ProviderPrompt;
import net.jrodolfo.llm.provider.ProviderPromptMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Builds provider-facing prompts for plain chat and tool-assisted turns.
 *
 * <p>Plain chat uses lightweight role-based history where available. Tool-assisted turns use a
 * more explicit grounded prompt so tool output can be summarized reliably by the selected model.
 */
@Service
public class ChatPromptBuilder {

    private final ObjectMapper objectMapper;

    /**
     * Constructs a new ChatPromptBuilder.
     *
     * @param objectMapper the object mapper for JSON formatting
     */
    public ChatPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds a prompt string based on the provided context.
     *
     * @param context the prompt context
     * @return the built prompt string
     */
    public String build(PromptContext context) {
        if (context.toolContext() == null) {
            return buildPlainChatPrompt(context.currentUserMessage(), context.history());
        }
        return buildToolAssistedPrompt(context.currentUserMessage(), context.history(), context.toolContext());
    }

    /**
     * Builds a plain chat prompt string.
     *
     * @param currentUserMessage the current user message
     * @param history            the conversation history
     * @return the built prompt string
     */
    public String buildPlainChatPrompt(String currentUserMessage, List<ChatSessionMessage> history) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are a concise, factual assistant.\n");
        builder.append("Use conversation history when it helps answer the current user message.\n");
        builder.append("Answer the current user message directly.\n");
        builder.append("Do not invent missing facts.\n");
        builder.append("If the context is incomplete or ambiguous, say so explicitly.\n");
        appendPlainConversationHistory(builder, history);
        builder.append("\nUser: ").append(currentUserMessage == null ? "" : currentUserMessage.trim()).append("\n");
        builder.append("Assistant:");
        return builder.toString();
    }

    /**
     * Builds a plain chat provider prompt object.
     *
     * @param currentUserMessage the current user message
     * @param history            the conversation history
     * @return the provider prompt object
     */
    public ProviderPrompt buildPlainChatProviderPrompt(String currentUserMessage, List<ChatSessionMessage> history) {
        List<ProviderPromptMessage> messages = new java.util.ArrayList<>();
        messages.add(new ProviderPromptMessage("system", """
                You are a concise, factual assistant.
                Answer the user's question directly.
                Use prior conversation when it helps.
                Do not invent missing facts.
                If the context is incomplete or ambiguous, say so explicitly.
                """.trim()));
        if (history != null) {
            for (ChatSessionMessage message : history) {
                if (message.role() == null || message.content() == null || message.content().isBlank()) {
                    continue;
                }
                String role = normalizeRole(message.role());
                messages.add(new ProviderPromptMessage(role, message.content()));
            }
        }
        messages.add(new ProviderPromptMessage("user", currentUserMessage == null ? "" : currentUserMessage.trim()));
        return ProviderPrompt.forMessages(messages, buildPlainChatPrompt(currentUserMessage, history));
    }

    /**
     * Builds a tool-assisted prompt string.
     *
     * @param currentUserMessage the current user message
     * @param history            the conversation history
     * @param toolContext        the tool context
     * @return the built prompt string
     */
    public String buildToolAssistedPrompt(String currentUserMessage, List<ChatSessionMessage> history, ToolContext toolContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("<assistant_instructions>\n");
        builder.append("You are a concise, factual assistant.\n");
        builder.append("Use conversation history when it helps answer the current user message.\n");
        builder.append("</assistant_instructions>\n");

        appendConversationHistory(builder, history);
        appendCurrentUserMessage(builder, currentUserMessage);
        appendToolContext(builder, toolContext);
        appendResponseRules(builder);
        return builder.toString();
    }

    /**
     * Appends plain conversation history to the prompt builder.
     *
     * @param builder the string builder
     * @param history the conversation history
     */
    private void appendPlainConversationHistory(StringBuilder builder, List<ChatSessionMessage> history) {
        if (history == null || history.isEmpty()) {
            return;
        }

        builder.append("\nConversation so far:\n");
        for (ChatSessionMessage message : history) {
            String role = "assistant".equalsIgnoreCase(message.role()) ? "Assistant" : "User";
            builder.append(role).append(": ").append(message.content()).append("\n");
        }
    }

    /**
     * Appends conversation history with tags to the prompt builder.
     *
     * @param builder the string builder
     * @param history the conversation history
     */
    private void appendConversationHistory(StringBuilder builder, List<ChatSessionMessage> history) {
        if (history == null || history.isEmpty()) {
            return;
        }

        builder.append("\n<conversation_history>\n");
        for (ChatSessionMessage message : history) {
            builder.append(message.role()).append(": ").append(message.content()).append("\n");
        }
        builder.append("</conversation_history>\n");
    }

    /**
     * Appends the current user message with tags to the prompt builder.
     *
     * @param builder            the string builder
     * @param currentUserMessage the current user message
     */
    private void appendCurrentUserMessage(StringBuilder builder, String currentUserMessage) {
        builder.append("\n<current_user_message>\n");
        builder.append(currentUserMessage == null ? "" : currentUserMessage.trim()).append("\n");
        builder.append("</current_user_message>\n");
    }

    /**
     * Appends tool context with tags to the prompt builder.
     *
     * @param builder     the string builder
     * @param toolContext the tool context
     */
    private void appendToolContext(StringBuilder builder, ToolContext toolContext) {
        if (toolContext == null) {
            return;
        }

        builder.append("\n<tool_context>\n");
        builder.append("tool_reason: ").append(nullToEmpty(toolContext.reason())).append("\n");
        builder.append("tool_name: ").append(nullToEmpty(toolContext.name())).append("\n");
        builder.append("tool_summary: ").append(nullToEmpty(toolContext.summary())).append("\n");
        builder.append("tool_result_json:\n");
        builder.append(formatResult(toolContext.result())).append("\n");
        builder.append("</tool_context>\n");
    }

    /**
     * Appends response rules with tags to the prompt builder.
     *
     * @param builder the string builder
     */
    private void appendResponseRules(StringBuilder builder) {
        builder.append("\n<response_rules>\n");
        builder.append("- Answer the current user message directly.\n");
        builder.append("- If tool output is present, ground your answer in it.\n");
        builder.append("- If tool output is present, summarize what the tool already completed instead of refusing or redirecting.\n");
        builder.append("- When the tool output includes counts, regions, services, bucket names, or artifact paths, prefer those concrete facts.\n");
        builder.append("- If tool_name is aws_region_audit and tool_result_json includes bucketNames, list those bucket names directly.\n");
        builder.append("- If bucketNames are available, suggest this exact next step: `run an S3 report for <bucket-name> for the last month`.\n");
        builder.append("- Do not tell the user to ask for summary.json, report.txt, or an artifact path when bucketNames are already available.\n");
        builder.append("- Do not claim inability or lack of access when tool output is already available in the prompt.\n");
        builder.append("- Do not mention generic safety, privacy, or policy concerns unless the tool output itself requires it.\n");
        builder.append("- If artifacts were generated, mention the relevant run directory or artifact path when it helps the user.\n");
        builder.append("- Do not invent missing facts.\n");
        builder.append("- If the context is incomplete or ambiguous, say so explicitly.\n");
        builder.append("</response_rules>\n");
    }

    /**
     * Formats the tool result map as a JSON string.
     *
     * @param result the result map
     * @return the formatted JSON string
     */
    private String formatResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "{}";
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return String.valueOf(result);
        }
    }

    /**
     * Returns an empty string if the provided value is null.
     *
     * @param value the string value
     * @return the value or an empty string
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Normalizes the role name for the model provider.
     *
     * @param role the raw role name
     * @return the normalized role name
     */
    private String normalizeRole(String role) {
        if ("assistant".equalsIgnoreCase(role)) {
            return "assistant";
        }
        return "user";
    }

    /**
     * Legacy-friendly wrapper for callers that still think in terms of a single text prompt.
     *
     * @param currentUserMessage the current user message
     * @param history            the conversation history
     * @param toolContext        the tool context
     */
    public record PromptContext(
            String currentUserMessage,
            List<ChatSessionMessage> history,
            ToolContext toolContext
    ) {
    }

    /**
     * Structured tool facts forwarded into a tool-assisted prompt.
     *
     * @param name    the tool name
     * @param reason  the reason for using the tool
     * @param summary a summary of the tool result
     * @param result  the tool result map
     */
    public record ToolContext(
            String name,
            String reason,
            String summary,
            Map<String, Object> result
    ) {
    }
}
