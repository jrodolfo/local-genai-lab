package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Session summary used by the sidebar session list.
 *
 * @param sessionId    stable local session identifier
 * @param title        short display title
 * @param summary      short display summary
 * @param mode         session mode such as {@code chat} or {@code rag}
 * @param provider     last model provider found in session message metadata
 * @param model        last resolved model stored on the session
 * @param createdAt    session creation timestamp
 * @param updatedAt    timestamp of the latest persisted message
 * @param messageCount number of persisted messages in the session
 */
@Schema(description = "Session summary used by the sidebar session list.")
public record ChatSessionSummaryResponse(
        String sessionId,
        String title,
        String summary,
        String mode,
        String provider,
        String model,
        Instant createdAt,
        Instant updatedAt,
        int messageCount
) {
}
