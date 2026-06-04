package net.jrodolfo.llm.rag.qdrant;

/**
 * Lightweight collection metadata returned by Qdrant.
 *
 * @param exists     whether the configured collection exists
 * @param pointCount number of indexed points, or null when unavailable
 */
public record QdrantCollectionInfo(
        boolean exists,
        Long pointCount
) {
    public static QdrantCollectionInfo missing() {
        return new QdrantCollectionInfo(false, null);
    }
}
