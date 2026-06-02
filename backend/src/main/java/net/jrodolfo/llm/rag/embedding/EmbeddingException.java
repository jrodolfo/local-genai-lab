package net.jrodolfo.llm.rag.embedding;

/**
 * Exception raised when an embedding runtime cannot produce a usable vector.
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
