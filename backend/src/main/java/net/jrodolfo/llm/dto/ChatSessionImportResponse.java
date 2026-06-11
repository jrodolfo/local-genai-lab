package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of importing a JSON chat session export.
 *
 * @param sessionId    stored session id after import
 * @param title        display title for the imported session
 * @param summary      display summary for the imported session
 * @param idChanged    true when the import id was invalid or already existed locally
 * @param messageCount number of messages imported
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
