package net.jrodolfo.llm.rag.dto;

public record RagSourceChunkResponse(
        String sourcePath,
        String title,
        String excerpt,
        double score
) {
}
