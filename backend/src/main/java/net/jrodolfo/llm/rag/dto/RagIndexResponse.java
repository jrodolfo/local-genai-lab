package net.jrodolfo.llm.rag.dto;

public record RagIndexResponse(
        String corpusRoot,
        int documentCount,
        int chunkCount,
        String retrievalMode
) {
}
