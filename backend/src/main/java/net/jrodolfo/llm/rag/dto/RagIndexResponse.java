package net.jrodolfo.llm.rag.dto;

/**
 * Response DTO providing details about the indexing result.
 *
 * @param corpusRoot    The root directory of the indexed corpus.
 * @param documentCount The number of documents that were indexed.
 * @param chunkCount    The total number of chunks created during indexing.
 * @param retrievalMode The retrieval mode currently in use.
 */
public record RagIndexResponse(
        String corpusRoot,
        int documentCount,
        int chunkCount,
        String retrievalMode
) {
}
