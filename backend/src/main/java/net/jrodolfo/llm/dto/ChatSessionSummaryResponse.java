package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Session summary used by the sidebar session list.")
public record ChatSessionSummaryResponse(
        String sessionId,
        String title,
        String summary,
        String mode,
        String model,
        Instant createdAt,
        Instant updatedAt,
        int messageCount
) {
}
