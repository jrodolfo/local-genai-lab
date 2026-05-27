package net.jrodolfo.llm.model;

public record ChatRagSourceChunk(
        String sourcePath,
        String title,
        String excerpt,
        double score
) {
}
