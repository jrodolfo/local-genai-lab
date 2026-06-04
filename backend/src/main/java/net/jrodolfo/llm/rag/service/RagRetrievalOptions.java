package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.config.RagRetrievalMode;
import net.jrodolfo.llm.rag.config.RagVectorStoreMode;

/**
 * Request-scoped RAG retrieval settings.
 */
public record RagRetrievalOptions(
        RagRetrievalMode retrievalMode,
        RagVectorStoreMode vectorStore
) {

    public static RagRetrievalOptions fromConfig(RagProperties ragProperties) {
        return fromRequest(ragProperties, null, null);
    }

    public static RagRetrievalOptions fromRequest(
            RagProperties ragProperties,
            String requestedRetrievalMode,
            String requestedVectorStore
    ) {
        RagRetrievalMode mode = isBlank(requestedRetrievalMode)
                ? RagRetrievalMode.fromConfig(ragProperties.retrievalMode())
                : RagRetrievalMode.fromConfig(requestedRetrievalMode);
        RagVectorStoreMode store = isBlank(requestedVectorStore)
                ? RagVectorStoreMode.fromConfig(ragProperties.vectorStore())
                : RagVectorStoreMode.fromConfig(requestedVectorStore);
        return new RagRetrievalOptions(mode, store);
    }

    public String indexKey() {
        if (retrievalMode == RagRetrievalMode.LEXICAL) {
            return retrievalMode.configValue();
        }
        return retrievalMode.configValue() + ":" + vectorStore.configValue();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
