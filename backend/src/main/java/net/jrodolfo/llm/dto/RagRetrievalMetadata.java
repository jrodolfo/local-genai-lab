package net.jrodolfo.llm.dto;

/**
 * Retrieval target metadata for a RAG answer.
 *
 * @param retrievalMode retrieval mode used for the answer
 * @param vectorStore vector store used when retrieval mode is vector
 * @param retrievalTarget stable selector value, such as vector:qdrant
 */
public record RagRetrievalMetadata(
        String retrievalMode,
        String vectorStore,
        String retrievalTarget
) {
}
