package net.jrodolfo.llm.rag.dto;

/**
 * Response DTO containing current RAG readiness and default retrieval configuration.
 *
 * @param enabled       Whether the RAG system is currently enabled.
 * @param indexed       Whether the backend default retrieval target has been indexed.
 * @param corpusRoot    The root directory of the document corpus.
 * @param documentCount The number of documents loaded in the system.
 * @param chunkCount    The total number of text chunks generated from the documents.
 * @param retrievalMode     The backend default retrieval mode.
 * @param retrievalStore    The storage/index style used by the retrieval implementation.
 * @param vectorStore       The configured vector store for vector retrieval.
 * @param qdrantUrl         The configured Qdrant endpoint.
 * @param qdrantCollection  The configured Qdrant collection name.
 * @param qdrantRequired    Whether Qdrant is required by the current RAG configuration.
 * @param qdrantReachable   Whether Qdrant is reachable, or null when it is not required.
 * @param qdrantCollectionExists Whether the configured Qdrant collection exists, or null when not checked.
 * @param qdrantPointCount  Number of points in the configured Qdrant collection, or null when unavailable.
 * @param qdrantStatusMessage A user-facing Qdrant readiness message.
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
        String vectorStore,
        String qdrantUrl,
        String qdrantCollection,
        boolean qdrantRequired,
        Boolean qdrantReachable,
        Boolean qdrantCollectionExists,
        Long qdrantPointCount,
        String qdrantStatusMessage,
        String embeddingProvider,
        String embeddingModel
) {
}
