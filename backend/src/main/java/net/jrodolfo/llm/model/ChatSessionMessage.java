package net.jrodolfo.llm.model;

import net.jrodolfo.llm.dto.ChatToolMetadata;

import java.time.Instant;

public record ChatSessionMessage(
        String role,
        String content,
        ChatToolMetadata tool,
        Instant timestamp
) {
}
