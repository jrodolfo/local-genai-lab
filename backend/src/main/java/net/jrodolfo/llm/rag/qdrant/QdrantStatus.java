package net.jrodolfo.llm.rag.qdrant;

/**
 * Readiness state for the configured Qdrant vector database.
 *
 * @param required whether Qdrant is required by the current RAG configuration
 * @param reachable whether Qdrant was reachable, or null when it was not required
 * @param collectionName configured collection name, or null when Qdrant is not required
 * @param collectionExists whether the configured collection exists, or null when not checked
 * @param pointCount indexed point count, or null when unavailable
 * @param message a user-facing readiness message
 */
public record QdrantStatus(
        boolean required,
        Boolean reachable,
        String collectionName,
        Boolean collectionExists,
        Long pointCount,
        String message
) {
    public static QdrantStatus notRequired() {
        return new QdrantStatus(false, null, null, null, null, "Qdrant is not required for the current RAG configuration.");
    }

    public static QdrantStatus collectionPresent(String collectionName, Long pointCount) {
        String pointText = pointCount == null ? "an unknown number of" : pointCount.toString();
        return new QdrantStatus(
                true,
                true,
                collectionName,
                true,
                pointCount,
                "Qdrant collection " + collectionName + " is present with " + pointText + " points."
        );
    }

    public static QdrantStatus collectionMissing(String collectionName) {
        return new QdrantStatus(
                true,
                true,
                collectionName,
                false,
                null,
                "Qdrant collection " + collectionName + " is missing. Rebuild the index."
        );
    }

    public static QdrantStatus unavailable(String qdrantUrl) {
        return new QdrantStatus(true, false, null, null, null, "Qdrant is not reachable at " + qdrantUrl + ".");
    }
}
