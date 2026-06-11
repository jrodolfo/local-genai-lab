package net.jrodolfo.llm.dto;

import net.jrodolfo.llm.model.ChatRagSourceChunk;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Message entry within a session detail or export payload.
 *
 * @param role         message role, currently {@code user} or {@code assistant}
 * @param content      message text shown in the UI and exports
 * @param tool         tool provenance stored on assistant messages when MCP tools are involved
 * @param toolResult   structured tool result used by artifact actions and grounded prompts
 * @param metadata     provider/model metadata stored on assistant messages
 * @param ragSources   cited RAG source chunks stored on RAG assistant messages
 * @param ragRetrieval retrieval target metadata stored on RAG assistant messages
 * @param ragTiming    RAG backend timing metadata stored on RAG assistant messages
 * @param timestamp    message creation timestamp
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
