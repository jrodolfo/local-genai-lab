package net.jrodolfo.llm.dto;

import java.time.Instant;
import java.util.List;

public record ChatSessionDetailResponse(
        String sessionId,
        String title,
        String model,
        Instant createdAt,
        Instant updatedAt,
        List<ChatSessionMessageResponse> messages,
        PendingToolCallResponse pendingTool
) {
}
