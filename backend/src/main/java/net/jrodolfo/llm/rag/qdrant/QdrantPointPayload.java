package net.jrodolfo.llm.rag.qdrant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Citation and debugging metadata stored with a Qdrant vector point.
 *
 * @param sourcePath source document path
 * @param chunkId source chunk id
 * @param title source document title
 * @param text source chunk text
 * @param corpusRoot indexed corpus root
 * @param embeddingProvider embedding provider used at index time
 * @param embeddingModel embedding model used at index time
 * @param indexedAt index timestamp
 * @param contentHash optional content hash for stale-index detection
 */
public record QdrantPointPayload(
        @JsonProperty("source_path")
        String sourcePath,
        @JsonProperty("chunk_id")
        String chunkId,
        String title,
        String text,
        @JsonProperty("corpus_root")
        String corpusRoot,
        @JsonProperty("embedding_provider")
        String embeddingProvider,
        @JsonProperty("embedding_model")
        String embeddingModel,
        @JsonProperty("indexed_at")
        String indexedAt,
        @JsonProperty("content_hash")
        String contentHash
) {
}
