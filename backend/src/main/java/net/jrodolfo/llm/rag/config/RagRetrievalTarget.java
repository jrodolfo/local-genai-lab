package net.jrodolfo.llm.rag.config;

import java.util.Arrays;

/**
 * User-selectable RAG retrieval target for a single RAG question.
 *
 * <p>The target combines the broad retrieval mode with the concrete backing
 * store. This lets the UI ask one question against lexical search, in-memory
 * vector search, or Qdrant-backed vector search without restarting the backend.
 */
public enum RagRetrievalTarget {
    LEXICAL("lexical", RagRetrievalMode.LEXICAL, RagVectorStoreMode.IN_MEMORY),
    VECTOR_IN_MEMORY("vector:in-memory", RagRetrievalMode.VECTOR, RagVectorStoreMode.IN_MEMORY),
    VECTOR_QDRANT("vector:qdrant", RagRetrievalMode.VECTOR, RagVectorStoreMode.QDRANT);

    private final String value;
    private final RagRetrievalMode retrievalMode;
    private final RagVectorStoreMode vectorStoreMode;

    RagRetrievalTarget(String value, RagRetrievalMode retrievalMode, RagVectorStoreMode vectorStoreMode) {
        this.value = value;
        this.retrievalMode = retrievalMode;
        this.vectorStoreMode = vectorStoreMode;
    }

    public String value() {
        return value;
    }

    public RagRetrievalMode retrievalMode() {
        return retrievalMode;
    }

    public RagVectorStoreMode vectorStoreMode() {
        return vectorStoreMode;
    }

    public boolean vector() {
        return retrievalMode == RagRetrievalMode.VECTOR;
    }

    /**
     * Returns the retrieval store label persisted with RAG answer metadata.
     *
     * @return a user-facing store label for exported sessions and technical details
     */
    public String retrievalStore() {
        return vector() ? "in-memory-vector" : retrievalMode.retrievalStore();
    }

    /**
     * Resolves a request-level retrieval target, falling back to startup RAG configuration.
     *
     * @param value      optional API request value such as {@code lexical} or {@code vector:qdrant}
     * @param properties configured backend defaults used when the request does not specify a target
     * @return the resolved retrieval target
     * @throws IllegalArgumentException if the request value is not supported
     */
    public static RagRetrievalTarget fromRequestOrDefault(String value, RagProperties properties) {
        if (value != null && !value.isBlank()) {
            return fromValue(value);
        }
        RagRetrievalMode mode = RagRetrievalMode.fromConfig(properties.retrievalMode());
        if (mode == RagRetrievalMode.LEXICAL) {
            return LEXICAL;
        }
        RagVectorStoreMode vectorStoreMode = RagVectorStoreMode.fromConfig(properties.vectorStore());
        return vectorStoreMode == RagVectorStoreMode.QDRANT ? VECTOR_QDRANT : VECTOR_IN_MEMORY;
    }

    /**
     * Parses the API value for a retrieval target.
     *
     * @param value target value from the frontend or API client
     * @return the matching retrieval target
     * @throws IllegalArgumentException if the value does not match a supported target
     */
    public static RagRetrievalTarget fromValue(String value) {
        if (value != null) {
            for (RagRetrievalTarget target : values()) {
                if (target.value.equalsIgnoreCase(value.trim())) {
                    return target;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported RAG retrieval target: " + value
                + ". Supported targets: " + supportedTargets() + ".");
    }

    private static String supportedTargets() {
        return String.join(", ", Arrays.stream(values())
                .map(RagRetrievalTarget::value)
                .toList());
    }
}
