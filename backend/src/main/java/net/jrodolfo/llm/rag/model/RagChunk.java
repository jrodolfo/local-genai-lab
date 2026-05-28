package net.jrodolfo.llm.rag.model;

/**
 * Represents a smaller chunk of text extracted from a {@link RagDocument}.
 *
 * @param id         A unique identifier for the chunk.
 * @param sourcePath The path of the source document this chunk belongs to.
 * @param title      The title of the source document.
 * @param text       The text content of this chunk.
 */
public record RagChunk(
        String id,
        String sourcePath,
        String title,
        String text
) {
}
