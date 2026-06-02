package net.jrodolfo.llm.rag.embedding;

/**
 * Converts text into an embedding vector for future vector-backed RAG retrieval.
 */
public interface EmbeddingService {

    /**
     * Embeds a single text input.
     *
     * @param text text to embed
     * @return embedding vector and model metadata
     */
    EmbeddingVector embed(String text);
}
