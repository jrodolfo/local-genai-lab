package net.jrodolfo.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.model.ChatSessionMessage;
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
        StringBuilder builder = new StringBuilder();
        builder.append("<assistant_instructions>\n");
        builder.append("You are a concise, factual assistant.\n");
        builder.append("Use conversation history when it helps answer the current user message.\n");
        builder.append("</assistant_instructions>\n");

        appendConversationHistory(builder, context.history());
        appendCurrentUserMessage(builder, context.currentUserMessage());
        appendToolContext(builder, context.toolContext());
        appendResponseRules(builder);

        return builder.toString();
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
