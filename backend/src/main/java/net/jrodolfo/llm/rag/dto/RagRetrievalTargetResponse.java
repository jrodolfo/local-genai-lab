package net.jrodolfo.llm.rag.dto;

/**
 * Describes one retrieval target that the RAG UI can select for a request.
 *
 * @param value stable UI value for the target selector
 * @param label user-facing label
 * @param retrievalMode request retrieval mode value
 * @param vectorStore request vector store value
 * @param available whether the target can be selected
 * @param ready whether the target has the required runtime/index state
 * @param message user-facing readiness details
 * @param pointCount indexed vector point count when available
 */
public record RagRetrievalTargetResponse(
        String value,
        String label,
        String retrievalMode,
        String vectorStore,
        boolean available,
        boolean ready,
        String message,
        Long pointCount
) {
}
