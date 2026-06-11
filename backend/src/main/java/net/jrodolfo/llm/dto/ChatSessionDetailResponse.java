package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Detailed session payload used when reopening, exporting, or importing a saved conversation.
 *
 * @param sessionId   stable local session identifier
 * @param title       display title, usually generated from the first user message
 * @param summary     display/export summary; may be generated from recent assistant content
 * @param mode        session mode such as {@code chat} or {@code rag}
 * @param model       last resolved model stored on the session
 * @param createdAt   session creation timestamp
 * @param updatedAt   timestamp of the latest persisted message
 * @param messages    full persisted message list in chronological order
 * @param pendingTool pending tool clarification state, if the next user turn should complete it
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
