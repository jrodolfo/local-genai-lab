package net.jrodolfo.llm.rag.model;

/**
 * Represents a match found during retrieval, associating a chunk with its relevance score.
 *
 * @param chunk The {@link RagChunk} that was matched.
 * @param score The similarity score of the match.
 */
public record RagMatch(
        RagChunk chunk,
        double score
) {
}
