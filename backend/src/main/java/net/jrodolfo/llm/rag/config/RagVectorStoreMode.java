package net.jrodolfo.llm.rag.config;

import java.util.Arrays;

/**
 * Supported stores for vector RAG retrieval.
 */
public enum RagVectorStoreMode {
    IN_MEMORY("in-memory"),
    QDRANT("qdrant");

    private final String configValue;

    RagVectorStoreMode(String configValue) {
        this.configValue = configValue;
    }

    public String configValue() {
        return configValue;
    }

    public static RagVectorStoreMode fromConfig(String value) {
        if (value != null) {
            for (RagVectorStoreMode mode : values()) {
                if (mode.configValue.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported RAG vector store: " + value
                + ". Supported stores: " + supportedStores() + ".");
    }

    private static String supportedStores() {
        return String.join(", ", Arrays.stream(values())
                .map(RagVectorStoreMode::configValue)
                .toList());
    }
}
