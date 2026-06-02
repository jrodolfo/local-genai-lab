package net.jrodolfo.llm.rag.config;

import java.util.Arrays;

/**
 * Supported RAG retrieval modes.
 */
public enum RagRetrievalMode {
    LEXICAL("lexical", "in-memory"),
    VECTOR("vector", "in-memory-vector");

    private final String configValue;
    private final String retrievalStore;

    RagRetrievalMode(String configValue, String retrievalStore) {
        this.configValue = configValue;
        this.retrievalStore = retrievalStore;
    }

    public String configValue() {
        return configValue;
    }

    public String retrievalStore() {
        return retrievalStore;
    }

    public static RagRetrievalMode fromConfig(String value) {
        if (value != null) {
            for (RagRetrievalMode mode : values()) {
                if (mode.configValue.equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported RAG retrieval mode: " + value
                + ". Supported modes: " + supportedModes() + ".");
    }

    private static String supportedModes() {
        return String.join(", ", Arrays.stream(values())
                .map(RagRetrievalMode::configValue)
                .toList());
    }
}
