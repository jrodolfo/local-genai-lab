package net.jrodolfo.llm.dto;

/**
 * Retrieval target metadata for a RAG answer.
 *
 * @param retrievalMode    retrieval mode used for the answer
 * @param retrievalStore   storage/index style used by the retrieval implementation
 * @param vectorStore      configured vector store when vector retrieval is selected
 * @param retrievalTarget  stable selector value, such as lexical or vector:qdrant
 * @param topK             maximum number of chunks requested from retrieval
 * @param embeddingProvider embedding provider used when vector retrieval is selected
 * @param embeddingModel   embedding model used when vector retrieval is selected
 */
public record RagRetrievalMetadata(
        String retrievalMode,
        String retrievalStore,
        String vectorStore,
        String retrievalTarget,
        Integer topK,
        String embeddingProvider,
        String embeddingModel
) {
}
