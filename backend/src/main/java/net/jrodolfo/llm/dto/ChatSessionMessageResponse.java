package net.jrodolfo.llm.dto;

import java.time.Instant;

public record ChatSessionMessageResponse(
        String role,
        String content,
        ChatToolMetadata tool,
        Instant timestamp
) {
}
