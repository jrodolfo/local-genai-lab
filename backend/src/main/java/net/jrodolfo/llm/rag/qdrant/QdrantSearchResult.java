package net.jrodolfo.llm.rag.qdrant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Search result returned by Qdrant for a stored RAG chunk.
 *
 * @param id point id
 * @param score vector similarity score
 * @param payload citation and debugging metadata
 */
public record QdrantSearchResult(
        String id,
        double score,
        @JsonProperty("payload")
        QdrantPointPayload payload
) {
}
