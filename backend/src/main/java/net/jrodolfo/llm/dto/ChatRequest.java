package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Chat request for normal or streaming chat endpoints.
 */
@Schema(description = "Chat request for normal or streaming chat endpoints.")
public record ChatRequest(
        @Schema(description = "User message to send to the chat model.", example = "Explain recursion with a simple example.")
        @NotBlank(message = "message is required") String message,
        @Schema(description = "Optional provider override. Falls back to the configured backend default when omitted.", example = "ollama")
        String provider,
        @Schema(description = "Optional model override. Falls back to the active provider default when omitted.", example = "llama3:8b")
        String model,
        @Schema(description = "Optional existing session identifier for continuing a saved conversation.", example = "session-123")
        String sessionId
) {
}
