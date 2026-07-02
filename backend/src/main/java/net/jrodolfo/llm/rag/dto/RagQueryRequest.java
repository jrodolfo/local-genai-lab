package net.jrodolfo.llm.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for submitting a question to the RAG system.
 *
 * @param question  The question to be answered using the RAG process.
 * @param provider  The preferred LLM provider.
 * @param model     The preferred model name.
 * @param sessionId The session identifier to maintain conversation context.
 * @param retrievalTarget Optional per-question retrieval target. Supported values are
 *                        {@code lexical}, {@code vector:in-memory}, and {@code vector:qdrant}.
 *                        When omitted, the backend uses the configured RAG retrieval defaults.
 */
@Schema(description = "Request body for one persisted RAG question.")
public record RagQueryRequest(
        @Schema(description = "Question to answer from the local documentation corpus.", example = "How does provider selection work?")
        @NotBlank(message = "question is required")
        String question,
        @Schema(description = "Optional provider override. Falls back to the configured backend default when omitted.", example = "ollama")
        String provider,
        @Schema(description = "Optional model override. Falls back to the selected provider default when omitted.", example = "llama3:8b")
        String model,
        @Schema(description = "Optional existing RAG session id. Omit to create a new RAG session.", example = "session-123")
        String sessionId,
        @Schema(description = "Optional per-question retrieval target. Supported values: lexical, vector:in-memory, vector:qdrant.", example = "vector:qdrant")
        String retrievalTarget
) {
}
