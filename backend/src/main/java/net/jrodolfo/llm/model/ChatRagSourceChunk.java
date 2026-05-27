package net.jrodolfo.llm.model;

/**
 * Represents a chunk of source content retrieved via RAG (Retrieval-Augmented Generation).
 *
 * @param sourcePath the path or identifier of the source document
 * @param title      the title of the source document
 * @param excerpt    the specific text excerpt from the document
 * @param score      the relevance score of this chunk
 */
public record ChatRagSourceChunk(
        String sourcePath,
        String title,
        String excerpt,
        double score
) {
}
