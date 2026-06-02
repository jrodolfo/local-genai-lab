package net.jrodolfo.llm.rag.dto;

/**
 * Response DTO containing the current status of the RAG (Retrieval-Augmented Generation) system.
 *
 * @param enabled       Whether the RAG system is currently enabled.
 * @param indexed       Whether the corpus has been indexed and is ready for retrieval.
 * @param corpusRoot    The root directory of the document corpus.
 * @param documentCount The number of documents loaded in the system.
 * @param chunkCount    The total number of text chunks generated from the documents.
 * @param retrievalMode     The current retrieval mode being used.
 * @param retrievalStore    The storage/index style used by the retrieval implementation.
 * @param embeddingProvider The configured embedding provider for vector retrieval.
 * @param embeddingModel    The configured embedding model for vector retrieval.
 */
public record RagStatusResponse(
        boolean enabled,
        boolean indexed,
        String corpusRoot,
        int documentCount,
        int chunkCount,
        String retrievalMode,
        String retrievalStore,
        String embeddingProvider,
        String embeddingModel
) {
}
