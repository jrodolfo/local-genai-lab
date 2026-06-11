package net.jrodolfo.llm.rag.embedding;

/**
 * Converts text into embedding vectors for active RAG vector retrieval.
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
