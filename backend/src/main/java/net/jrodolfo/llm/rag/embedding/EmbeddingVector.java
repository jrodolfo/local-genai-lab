package net.jrodolfo.llm.rag.embedding;

import java.util.List;
import java.util.Objects;

/**
 * A numeric representation of text meaning produced by an embedding model.
 *
 * @param model  embedding model used to produce the vector
 * @param values embedding dimensions
 */
public record EmbeddingVector(String model, List<Double> values) {

    public EmbeddingVector {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Embedding model must not be blank.");
        }
        Objects.requireNonNull(values, "Embedding values must not be null.");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Embedding values must not be empty.");
        }
        values = List.copyOf(values);
    }
}
