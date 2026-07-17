package net.jrodolfo.llm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jrodolfo.llm.dto.ChatSessionDetailResponse;
import net.jrodolfo.llm.dto.ChatSessionMessageResponse;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import net.jrodolfo.llm.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Converts stored session details into download formats.
 *
 * <p>JSON exports are pretty-printed and importable. Markdown exports are
 * optimized for human reading and include provider, tool, and RAG metadata when
 * present.
 */
@Service
public class ChatSessionExportService {

    private final ObjectMapper objectMapper;

    public ChatSessionExportService() {
        this(new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    @Autowired
    public ChatSessionExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts a session detail response to pretty-printed JSON bytes.
     *
     * <p>If the stored summary is truncated, the export replaces it with the
     * latest assistant message content when available.
     *
     * @param session session details loaded from storage
     * @return importable formatted JSON bytes
     */
    public byte[] toJson(ChatSessionDetailResponse session) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(exportSession(session))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to export chat session as JSON.", exception);
        }
    }

    /**
     * Converts a session detail response to Markdown for reading.
     *
     * @param session session details loaded from storage
     * @return human-readable Markdown export
     */
    public String toMarkdown(ChatSessionDetailResponse session) {
        ChatSessionDetailResponse exportSession = exportSession(session);
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(valueOrFallback(exportSession.title(), "Untitled session")).append("\n\n");

        if (StringUtils.hasText(exportSession.summary())) {
            markdown.append("## summary\n\n");
            markdown.append(exportSession.summary()).append("\n\n");
        }

        markdown.append("## session metadata\n\n");
        markdown.append("- session id: ").append(exportSession.sessionId()).append("\n");
        markdown.append("- mode: ").append(valueOrFallback(exportSession.mode(), "chat")).append("\n");
        markdown.append("- model: ").append(valueOrFallback(exportSession.model(), "unknown")).append("\n");
        markdown.append("- created at: ").append(formatInstant(exportSession.createdAt())).append("\n");
        markdown.append("- updated at: ").append(formatInstant(exportSession.updatedAt())).append("\n\n");

        appendPendingTool(markdown, exportSession.pendingTool());

        markdown.append("## conversation\n\n");
        for (ChatSessionMessageResponse message : exportSession.messages()) {
            markdown.append("### ").append(valueOrFallback(message.role(), "unknown")).append("\n\n");
            markdown.append("- timestamp: ").append(formatInstant(message.timestamp())).append("\n");

            if (message.tool() != null && message.tool().used()) {
                markdown.append("- tool: ").append(valueOrFallback(message.tool().name(), "unknown")).append("\n");
                if (StringUtils.hasText(message.tool().status())) {
                    markdown.append("- tool status: ").append(message.tool().status()).append("\n");
                }
                if (StringUtils.hasText(message.tool().summary())) {
                    markdown.append("- tool summary: ").append(message.tool().summary()).append("\n");
                }
            }

            appendProviderMetadata(markdown, message.metadata());
            appendRagRetrieval(markdown, message.ragRetrieval());
            appendRagTiming(markdown, message.ragTiming());
            appendRagSources(markdown, message.ragSources());
            markdown.append("\n");
            markdown.append(message.content() == null ? "" : message.content()).append("\n\n");
        }

        return markdown.toString().trim() + "\n";
    }

    private ChatSessionDetailResponse exportSession(ChatSessionDetailResponse session) {
        return new ChatSessionDetailResponse(
                session.sessionId(),
                session.title(),
                exportSummary(session),
                session.mode(),
                session.model(),
                session.createdAt(),
                session.updatedAt(),
                session.messages(),
                session.pendingTool()
        );
    }

    private String exportSummary(ChatSessionDetailResponse session) {
        if (!isTruncated(session.summary())) {
            return session.summary();
        }

        return latestAssistantContent(session.messages())
                .stream()
                .findFirst()
                .orElse(session.summary());
    }

    private List<String> latestAssistantContent(List<ChatSessionMessageResponse> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatSessionMessageResponse message = messages.get(index);
            if ("assistant".equals(message.role()) && StringUtils.hasText(message.content())) {
                return List.of(normalizeWhitespace(message.content()));
            }
        }

        return List.of();
    }

    private boolean isTruncated(String value) {
        return StringUtils.hasText(value) && value.endsWith("…");
    }

    /**
     * Appends information about a pending tool call to the Markdown builder.
     *
     * @param markdown    the Markdown builder
     * @param pendingTool the pending tool call details
     */
    private void appendPendingTool(StringBuilder markdown, PendingToolCallResponse pendingTool) {
        if (pendingTool == null) {
            return;
        }

        markdown.append("## pending tool\n\n");
        markdown.append("- awaiting tool: ").append(valueOrFallback(pendingTool.toolName(), "unknown")).append("\n");
        if (StringUtils.hasText(pendingTool.reason())) {
            markdown.append("- reason: ").append(pendingTool.reason()).append("\n");
        }
        if (pendingTool.missingFields() != null && !pendingTool.missingFields().isEmpty()) {
            markdown.append("- missing fields: ").append(String.join(", ", pendingTool.missingFields())).append("\n");
        }
        markdown.append("\n");
    }

    /**
     * Appends model provider metadata to the Markdown builder.
     *
     * @param markdown the Markdown builder
     * @param metadata the provider metadata
     */
    private void appendProviderMetadata(StringBuilder markdown, ModelProviderMetadata metadata) {
        if (metadata == null) {
            return;
        }

        if (StringUtils.hasText(metadata.provider())) {
            markdown.append("- provider: ").append(metadata.provider()).append("\n");
        }
        if (StringUtils.hasText(metadata.modelId())) {
            markdown.append("- provider model: ").append(metadata.modelId()).append("\n");
        }
        if (StringUtils.hasText(metadata.stopReason())) {
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

    /**
     * Appends RAG retrieval metadata to the Markdown builder.
     *
     * @param markdown     the Markdown builder
     * @param ragRetrieval the RAG retrieval metadata
     */
    private void appendRagRetrieval(StringBuilder markdown, net.jrodolfo.llm.dto.RagRetrievalMetadata ragRetrieval) {
        if (ragRetrieval == null) {
            return;
        }
        if (StringUtils.hasText(ragRetrieval.retrievalMode())) {
            markdown.append("- rag retrieval mode: ").append(ragRetrieval.retrievalMode()).append("\n");
        }
        if (StringUtils.hasText(ragRetrieval.retrievalStore())) {
            markdown.append("- rag retrieval store: ").append(ragRetrieval.retrievalStore()).append("\n");
        }
        if (StringUtils.hasText(ragRetrieval.vectorStore())) {
            markdown.append("- rag vector store: ").append(ragRetrieval.vectorStore()).append("\n");
        }
        if (StringUtils.hasText(ragRetrieval.retrievalTarget())) {
            markdown.append("- rag retrieval target: ").append(ragRetrieval.retrievalTarget()).append("\n");
        }
        if (ragRetrieval.topK() != null) {
            markdown.append("- rag top k: ").append(ragRetrieval.topK()).append("\n");
        }
        if (StringUtils.hasText(ragRetrieval.embeddingProvider())) {
            markdown.append("- rag embedding provider: ").append(ragRetrieval.embeddingProvider()).append("\n");
        }
        if (StringUtils.hasText(ragRetrieval.embeddingModel())) {
            markdown.append("- rag embedding model: ").append(ragRetrieval.embeddingModel()).append("\n");
        }
    }

    /**
     * Appends RAG backend timing metadata to the Markdown builder.
     *
     * @param markdown  the Markdown builder
     * @param ragTiming the RAG timing metadata
     */
    private void appendRagTiming(StringBuilder markdown, net.jrodolfo.llm.dto.RagTimingMetadata ragTiming) {
        if (ragTiming == null) {
            return;
        }
        if (ragTiming.retrievalDurationMs() != null) {
            markdown.append("- rag retrieval duration: ").append(formatDurationMs(ragTiming.retrievalDurationMs())).append("\n");
        }
        if (ragTiming.providerDurationMs() != null) {
            markdown.append("- rag provider duration: ").append(formatDurationMs(ragTiming.providerDurationMs())).append("\n");
        }
        if (ragTiming.totalDurationMs() != null) {
            markdown.append("- rag backend total: ").append(formatDurationMs(ragTiming.totalDurationMs())).append("\n");
        }
    }

    /**
     * Appends RAG sources to the Markdown builder.
     *
     * @param markdown   the Markdown builder
     * @param ragSources the list of RAG sources
     */
    private void appendRagSources(StringBuilder markdown, java.util.List<net.jrodolfo.llm.model.ChatRagSourceChunk> ragSources) {
        if (ragSources == null || ragSources.isEmpty()) {
            return;
        }
        markdown.append("- rag sources:\n");
        for (net.jrodolfo.llm.model.ChatRagSourceChunk source : ragSources) {
            markdown.append("  - ")
                    .append(valueOrFallback(source.title(), "source"))
                    .append(" (")
                    .append(valueOrFallback(source.sourcePath(), "unknown"))
                    .append(", score ")
                    .append(source.score())
                    .append(")\n");
        }
    }

    /**
     * Formats an Instant as a string.
     *
     * @param instant the instant to format
     * @return the formatted string
     */
    private String formatInstant(Instant instant) {
        return instant == null ? "unknown" : instant.toString();
    }

    /**
     * Formats a millisecond duration for Markdown export.
     *
     * @param durationMs duration in milliseconds
     * @return formatted duration
     */
    private String formatDurationMs(Long durationMs) {
        if (durationMs == null) {
            return "unknown";
        }
        if (durationMs == 0L) {
            return "<1 ms";
        }
        return durationMs + " ms";
    }

    /**
     * Returns the value or a fallback string if the value is blank.
     *
     * @param value    the string value
     * @param fallback the fallback string
     * @return the value or the fallback
     */
    private String valueOrFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

}
