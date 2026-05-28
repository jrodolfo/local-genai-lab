package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Detailed session payload used when reopening a saved conversation.
 */
@Schema(description = "Detailed session payload used when reopening a saved conversation.")
public record ChatSessionDetailResponse(
        String sessionId,
        String title,
        String summary,
        String mode,
        String model,
        Instant createdAt,
        Instant updatedAt,
        List<ChatSessionMessageResponse> messages,
        PendingToolCallResponse pendingTool
) {
}
