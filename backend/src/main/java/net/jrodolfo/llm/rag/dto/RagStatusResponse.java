package net.jrodolfo.llm.rag.dto;

public record RagStatusResponse(
        boolean enabled,
        boolean indexed,
        String corpusRoot,
        int documentCount,
        int chunkCount,
        String retrievalMode
) {
}
