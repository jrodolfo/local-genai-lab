package net.jrodolfo.llm.dto;

import net.jrodolfo.llm.model.ChatRagSourceChunk;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Message entry within a chat session.
 */
public record ChatSessionMessageResponse(
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
    public ChatSessionMessageResponse(
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

    public ChatSessionMessageResponse(
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
