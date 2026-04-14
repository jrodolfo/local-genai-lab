package net.jrodolfo.llm.service;

import net.jrodolfo.llm.dto.ChatSessionDetailResponse;
import net.jrodolfo.llm.dto.ChatSessionMessageResponse;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ChatSessionExportService {

    public String toMarkdown(ChatSessionDetailResponse session) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(valueOrFallback(session.title(), "Untitled session")).append("\n\n");

        if (hasText(session.summary())) {
            markdown.append("## summary\n\n");
            markdown.append(session.summary()).append("\n\n");
        }

        markdown.append("## session metadata\n\n");
        markdown.append("- session id: ").append(session.sessionId()).append("\n");
        markdown.append("- model: ").append(valueOrFallback(session.model(), "unknown")).append("\n");
        markdown.append("- created at: ").append(formatInstant(session.createdAt())).append("\n");
        markdown.append("- updated at: ").append(formatInstant(session.updatedAt())).append("\n\n");

        appendPendingTool(markdown, session.pendingTool());

        markdown.append("## conversation\n\n");
        for (ChatSessionMessageResponse message : session.messages()) {
            markdown.append("### ").append(valueOrFallback(message.role(), "unknown")).append("\n\n");
            markdown.append("- timestamp: ").append(formatInstant(message.timestamp())).append("\n");

            if (message.tool() != null && message.tool().used()) {
                markdown.append("- tool: ").append(valueOrFallback(message.tool().name(), "unknown")).append("\n");
                if (hasText(message.tool().status())) {
                    markdown.append("- tool status: ").append(message.tool().status()).append("\n");
                }
                if (hasText(message.tool().summary())) {
                    markdown.append("- tool summary: ").append(message.tool().summary()).append("\n");
                }
            }

            appendProviderMetadata(markdown, message.metadata());
            markdown.append("\n");
            markdown.append(message.content() == null ? "" : message.content()).append("\n\n");
        }

        return markdown.toString().trim() + "\n";
    }

    private void appendPendingTool(StringBuilder markdown, PendingToolCallResponse pendingTool) {
        if (pendingTool == null) {
            return;
        }

        markdown.append("## pending tool\n\n");
        markdown.append("- awaiting tool: ").append(valueOrFallback(pendingTool.toolName(), "unknown")).append("\n");
        if (hasText(pendingTool.reason())) {
            markdown.append("- reason: ").append(pendingTool.reason()).append("\n");
        }
        if (pendingTool.missingFields() != null && !pendingTool.missingFields().isEmpty()) {
            markdown.append("- missing fields: ").append(String.join(", ", pendingTool.missingFields())).append("\n");
        }
        markdown.append("\n");
    }

    private void appendProviderMetadata(StringBuilder markdown, ModelProviderMetadata metadata) {
        if (metadata == null) {
            return;
        }

        if (hasText(metadata.provider())) {
            markdown.append("- provider: ").append(metadata.provider()).append("\n");
        }
        if (hasText(metadata.modelId())) {
            markdown.append("- provider model: ").append(metadata.modelId()).append("\n");
        }
        if (hasText(metadata.stopReason())) {
            markdown.append("- stop reason: ").append(metadata.stopReason()).append("\n");
        }
        if (metadata.inputTokens() != null || metadata.outputTokens() != null || metadata.totalTokens() != null) {
            markdown.append("- tokens: ")
                    .append(metadata.inputTokens() != null ? metadata.inputTokens() : "?")
                    .append(" in / ")
                    .append(metadata.outputTokens() != null ? metadata.outputTokens() : "?")
                    .append(" out / ")
                    .append(metadata.totalTokens() != null ? metadata.totalTokens() : "?")
                    .append(" total\n");
        }
        if (metadata.durationMs() != null) {
            markdown.append("- provider duration: ").append(metadata.durationMs()).append(" ms\n");
        }
        if (metadata.providerLatencyMs() != null) {
            markdown.append("- provider latency: ").append(metadata.providerLatencyMs()).append(" ms\n");
        }
        if (metadata.backendDurationMs() != null) {
            markdown.append("- backend total: ").append(metadata.backendDurationMs()).append(" ms\n");
        }
        if (metadata.uiWaitMs() != null) {
            markdown.append("- ui wait: ").append(metadata.uiWaitMs()).append(" ms\n");
        }
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "unknown" : instant.toString();
    }

    private String valueOrFallback(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
