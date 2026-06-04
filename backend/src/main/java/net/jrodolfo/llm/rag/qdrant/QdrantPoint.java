package net.jrodolfo.llm.rag.qdrant;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A Qdrant point containing one embedded RAG chunk and citation payload.
 *
 * @param id stable point identifier
 * @param vector embedding vector values
 * @param payload citation and debugging metadata
 */
public record QdrantPoint(
        String id,
        List<Double> vector,
        @JsonProperty("payload")
        QdrantPointPayload payload
) {
    public QdrantPoint {
        vector = List.copyOf(vector);
    }
}
