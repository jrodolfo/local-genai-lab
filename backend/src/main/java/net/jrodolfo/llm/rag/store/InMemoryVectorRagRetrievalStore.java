package net.jrodolfo.llm.rag.store;

import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagEmbeddedChunk;
import net.jrodolfo.llm.rag.model.RagMatch;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * In-memory vector retrieval store for active local RAG vector experiments.
 *
 * <p>This store is intentionally separate from the active lexical store. It
 * receives already-embedded chunks and ranks them against a query embedding
 * using cosine similarity.
 */
@Component
public class InMemoryVectorRagRetrievalStore {

    private volatile List<RagEmbeddedChunk> indexedChunks = List.of();

    /**
     * Replaces all embedded chunks in the vector index.
     *
     * @param chunks embedded chunks to index
     */
    public void replaceAllEmbedded(List<RagEmbeddedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            indexedChunks = List.of();
            return;
        }
        int expectedDimension = chunks.getFirst().vectorDimension();
        for (RagEmbeddedChunk chunk : chunks) {
            if (chunk.vectorDimension() != expectedDimension) {
                throw new IllegalArgumentException("All embedded chunks must have the same vector dimension.");
            }
        }
        indexedChunks = List.copyOf(chunks);
    }

    /**
     * Searches indexed chunks by query vector.
     *
     * @param queryVector query embedding vector
     * @param topK        maximum number of matches
     * @return relevant matches sorted by descending similarity
     */
    public List<RagMatch> searchByVector(List<Double> queryVector, int topK) {
        if (queryVector == null || queryVector.isEmpty() || topK <= 0 || isZeroVector(queryVector)) {
            return List.of();
        }

        return indexedChunks.stream()
                .filter(chunk -> chunk.vectorDimension() == queryVector.size())
                .map(chunk -> new RagMatch(toRagChunk(chunk), cosineSimilarity(queryVector, chunk.vector())))
                .filter(match -> match.score() > 0.0d)
                .sorted(Comparator.comparingDouble(RagMatch::score).reversed())
                .limit(topK)
                .toList();
    }

    private static RagChunk toRagChunk(RagEmbeddedChunk embeddedChunk) {
        return new RagChunk(
                embeddedChunk.chunkId(),
                embeddedChunk.sourcePath(),
                embeddedChunk.title(),
                embeddedChunk.text()
        );
    }

    private static double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.size() != right.size() || isZeroVector(right)) {
            return 0.0d;
        }

        double dotProduct = 0.0d;
        for (int index = 0; index < left.size(); index++) {
            dotProduct += vectorValue(left.get(index)) * vectorValue(right.get(index));
        }
        if (dotProduct == 0.0d) {
            return 0.0d;
        }
        return dotProduct / (vectorMagnitude(left) * vectorMagnitude(right));
    }

    private static boolean isZeroVector(List<Double> vector) {
        for (Double value : vector) {
            if (value != null && value != 0.0d) {
                return false;
            }
        }
        return true;
    }

    private static double vectorMagnitude(List<Double> vector) {
        double sum = 0.0d;
        for (Double value : vector) {
            if (value != null) {
                sum += vectorValue(value) * vectorValue(value);
            }
        }
        return Math.sqrt(sum);
    }

    private static double vectorValue(Double value) {
        return value == null ? 0.0d : value;
    }
}
