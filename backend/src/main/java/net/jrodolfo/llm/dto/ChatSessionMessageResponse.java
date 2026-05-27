package net.jrodolfo.llm.dto;

import net.jrodolfo.llm.model.ChatRagSourceChunk;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChatSessionMessageResponse(
        String role,
        String content,
        ChatToolMetadata tool,
        Map<String, Object> toolResult,
        ModelProviderMetadata metadata,
        List<ChatRagSourceChunk> ragSources,
        Instant timestamp
) {
}
