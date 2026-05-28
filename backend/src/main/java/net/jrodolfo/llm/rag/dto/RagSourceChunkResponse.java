package net.jrodolfo.llm.rag.dto;

/**
 * Response DTO representing a specific chunk of source material retrieved for a RAG query.
 *
 * @param sourcePath The file path or identifier of the source document.
 * @param title      The title or heading associated with the chunk.
 * @param excerpt    The actual text content of the chunk.
 * @param score      The relevance score of this chunk relative to the query.
 */
public record RagSourceChunkResponse(
        String sourcePath,
        String title,
        String excerpt,
        double score
) {
}
