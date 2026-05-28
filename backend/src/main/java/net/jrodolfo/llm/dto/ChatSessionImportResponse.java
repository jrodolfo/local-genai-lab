package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of importing a JSON chat session export.
 */
@Schema(description = "Result of importing a JSON chat session export.")
public record ChatSessionImportResponse(
        String sessionId,
        String title,
        String summary,
        boolean idChanged,
        int messageCount
) {
}
