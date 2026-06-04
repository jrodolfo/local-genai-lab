package net.jrodolfo.llm.rag.dto;

/**
 * Optional request DTO for rebuilding a specific RAG retrieval index.
 *
 * @param retrievalMode Optional request-scoped retrieval mode.
 * @param vectorStore Optional request-scoped vector store for vector retrieval.
 */
public record RagIndexRequest(
        String retrievalMode,
        String vectorStore
) {
}
