package net.jrodolfo.llm.model;

import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.RagRetrievalMetadata;
import net.jrodolfo.llm.dto.RagTimingMetadata;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persisted message within a local session.
 *
 * <p>Assistant messages may carry provider metadata, MCP tool metadata/results,
 * or RAG source metadata. User messages normally contain only role, content,
 * and timestamp.
 *
 * @param role       the role of the message sender, usually {@code user} or {@code assistant}
 * @param content    the text content of the message
 * @param tool       metadata about a tool call, if the message involves one
 * @param toolResult the result of a tool execution, if applicable
 * @param metadata   metadata from the model provider (e.g., token usage)
 * @param ragSources a list of RAG source chunks that informed this message
 * @param ragRetrieval retrieval target metadata for a RAG answer
 * @param ragTiming  backend timing metadata for a RAG answer
 * @param timestamp  the timestamp when the message was created
 */
public record ChatSessionMessage(
        String role,
        String content,
        ChatToolMetadata tool,
        Map<String, Object> toolResult,
        ModelProviderMetadata metadata,
        List<ChatRagSourceChunk> ragSources,
        RagRetrievalMetadata ragRetrieval,
        RagTimingMetadata ragTiming,
        Instant timestamp
) {
    public ChatSessionMessage(
            String role,
            String content,
            ChatToolMetadata tool,
            Map<String, Object> toolResult,
            ModelProviderMetadata metadata,
            List<ChatRagSourceChunk> ragSources,
            Instant timestamp
    ) {
        this(role, content, tool, toolResult, metadata, ragSources, null, null, timestamp);
    }

    public ChatSessionMessage(
            String role,
            String content,
            ChatToolMetadata tool,
            Map<String, Object> toolResult,
            ModelProviderMetadata metadata,
            List<ChatRagSourceChunk> ragSources,
            RagTimingMetadata ragTiming,
            Instant timestamp
    ) {
        this(role, content, tool, toolResult, metadata, ragSources, null, ragTiming, timestamp);
    }
}
