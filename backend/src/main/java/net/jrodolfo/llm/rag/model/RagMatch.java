package net.jrodolfo.llm.rag.model;

public record RagMatch(
        RagChunk chunk,
        double score
) {
}
