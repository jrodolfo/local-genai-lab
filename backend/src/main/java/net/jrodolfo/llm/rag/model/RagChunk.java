package net.jrodolfo.llm.rag.model;

public record RagChunk(
        String id,
        String sourcePath,
        String title,
        String text
) {
}
