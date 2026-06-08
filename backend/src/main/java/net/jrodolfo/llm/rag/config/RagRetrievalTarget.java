package net.jrodolfo.llm.rag.config;

import java.util.Arrays;

/**
 * User-selectable RAG retrieval targets.
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

    public String retrievalStore() {
        return vector() ? "in-memory-vector" : retrievalMode.retrievalStore();
    }

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
