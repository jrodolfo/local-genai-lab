package net.jrodolfo.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.model.ChatSessionMessage;
import net.jrodolfo.llm.provider.ProviderPrompt;
import net.jrodolfo.llm.provider.ProviderPromptMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatPromptBuilder {

    private final ObjectMapper objectMapper;

    public ChatPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(PromptContext context) {
        if (context.toolContext() == null) {
            return buildPlainChatPrompt(context.currentUserMessage(), context.history());
        }
        return buildToolAssistedPrompt(context.currentUserMessage(), context.history(), context.toolContext());
    }

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

    private void appendCurrentUserMessage(StringBuilder builder, String currentUserMessage) {
        builder.append("\n<current_user_message>\n");
        builder.append(currentUserMessage == null ? "" : currentUserMessage.trim()).append("\n");
        builder.append("</current_user_message>\n");
    }

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

    private void appendResponseRules(StringBuilder builder) {
        builder.append("\n<response_rules>\n");
        builder.append("- Answer the current user message directly.\n");
        builder.append("- If tool output is present, ground your answer in it.\n");
        builder.append("- If tool output is present, summarize what the tool already completed instead of refusing or redirecting.\n");
        builder.append("- When the tool output includes counts, regions, services, bucket names, or artifact paths, prefer those concrete facts.\n");
        builder.append("- Do not claim inability or lack of access when tool output is already available in the prompt.\n");
        builder.append("- Do not mention generic safety, privacy, or policy concerns unless the tool output itself requires it.\n");
        builder.append("- If artifacts were generated, mention the relevant run directory or artifact path when it helps the user.\n");
        builder.append("- Do not invent missing facts.\n");
        builder.append("- If the context is incomplete or ambiguous, say so explicitly.\n");
        builder.append("</response_rules>\n");
    }

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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalizeRole(String role) {
        if ("assistant".equalsIgnoreCase(role)) {
            return "assistant";
        }
        return "user";
    }

    public record PromptContext(
            String currentUserMessage,
            List<ChatSessionMessage> history,
            ToolContext toolContext
    ) {
    }

    public record ToolContext(
            String name,
            String reason,
            String summary,
            Map<String, Object> result
    ) {
    }
}
