package net.jrodolfo.llm.rag.model;

import java.util.List;
import java.util.Objects;

/**
 * A RAG chunk plus its embedding vector and indexing metadata.
 *
 * @param chunkId         source chunk id
 * @param sourcePath      source document path
 * @param title           source document title
 * @param text            source chunk text
 * @param embeddingModel  embedding model used for this vector
 * @param vectorDimension vector dimension count
 * @param vector          embedding values
 */
public record RagEmbeddedChunk(
        String chunkId,
        String sourcePath,
        String title,
        String text,
        String embeddingModel,
        int vectorDimension,
        List<Double> vector
) {

    public RagEmbeddedChunk {
        requireNotBlank(chunkId, "Embedded chunk id must not be blank.");
        requireNotBlank(sourcePath, "Embedded chunk source path must not be blank.");
        requireNotBlank(text, "Embedded chunk text must not be blank.");
        requireNotBlank(embeddingModel, "Embedded chunk embedding model must not be blank.");
        Objects.requireNonNull(vector, "Embedded chunk vector must not be null.");
        if (vector.isEmpty()) {
            throw new IllegalArgumentException("Embedded chunk vector must not be empty.");
        }
        if (vectorDimension != vector.size()) {
            throw new IllegalArgumentException("Embedded chunk vector dimension must match vector size.");
        }
        vector = List.copyOf(vector);
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
