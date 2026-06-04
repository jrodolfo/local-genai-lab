package net.jrodolfo.llm.rag.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for submitting a question to the RAG system.
 *
 * @param question  The question to be answered using the RAG process.
 * @param provider  The preferred LLM provider.
 * @param model     The preferred model name.
 * @param sessionId The session identifier to maintain conversation context.
 * @param retrievalMode Optional request-scoped retrieval mode.
 * @param vectorStore Optional request-scoped vector store for vector retrieval.
 * @param persist Whether to save the turn in a RAG session. Null defaults to true.
 */
public record RagQueryRequest(
        @NotBlank(message = "question is required")
        String question,
        String provider,
        String model,
        String sessionId,
        String retrievalMode,
        String vectorStore,
        Boolean persist
) {
    public boolean shouldPersist() {
        return persist == null || persist;
    }
}
