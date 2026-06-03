package net.jrodolfo.llm.rag.qdrant;

/**
 * Readiness state for the configured Qdrant vector database.
 *
 * @param required whether Qdrant is required by the current RAG configuration
 * @param reachable whether Qdrant was reachable, or null when it was not required
 * @param message a user-facing readiness message
 */
public record QdrantStatus(
        boolean required,
        Boolean reachable,
        String message
) {
    public static QdrantStatus notRequired() {
        return new QdrantStatus(false, null, "Qdrant is not required for the current RAG configuration.");
    }

    public static QdrantStatus reachableStatus() {
        return new QdrantStatus(true, true, "Qdrant is reachable.");
    }

    public static QdrantStatus unavailable(String qdrantUrl) {
        return new QdrantStatus(true, false, "Qdrant is not reachable at " + qdrantUrl + ".");
    }
}
