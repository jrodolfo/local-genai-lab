package net.jrodolfo.llm.dto;

import java.util.List;

public record PendingToolCallResponse(
        String toolName,
        String reason,
        List<String> missingFields
) {
}
