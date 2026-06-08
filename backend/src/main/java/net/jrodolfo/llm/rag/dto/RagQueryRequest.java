package net.jrodolfo.llm.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for submitting a question to the RAG system.
 *
 * @param question  The question to be answered using the RAG process.
 * @param provider  The preferred LLM provider.
 * @param model     The preferred model name.
 * @param sessionId The session identifier to maintain conversation context.
 * @param retrievalTarget Optional retrieval target for this request.
 */
public record RagQueryRequest(
        @NotBlank(message = "question is required")
        String question,
        String provider,
        String model,
        String sessionId,
        String retrievalTarget
) {
}
