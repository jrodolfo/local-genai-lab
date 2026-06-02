package net.jrodolfo.llm.rag.model;

import java.util.List;
import java.util.Objects;

/**
 * Summary of an in-process vector indexing pass.
 *
 * @param embeddingProvider embedding runtime used for the index
 * @param embeddingModel    embedding model used for the index
 * @param chunkCount        number of embedded chunks
 * @param vectorDimension   vector dimension count
 * @param chunks            embedded chunks
 */
public record RagVectorIndexResult(
        String embeddingProvider,
        String embeddingModel,
        int chunkCount,
        int vectorDimension,
        List<RagEmbeddedChunk> chunks
) {

    public RagVectorIndexResult {
        requireNotBlank(embeddingProvider, "Vector index embedding provider must not be blank.");
        requireNotBlank(embeddingModel, "Vector index embedding model must not be blank.");
        Objects.requireNonNull(chunks, "Vector index chunks must not be null.");
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Vector index chunks must not be empty.");
        }
        if (chunkCount != chunks.size()) {
            throw new IllegalArgumentException("Vector index chunk count must match chunk list size.");
        }
        if (vectorDimension <= 0) {
            throw new IllegalArgumentException("Vector index dimension must be positive.");
        }
        chunks = List.copyOf(chunks);
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
