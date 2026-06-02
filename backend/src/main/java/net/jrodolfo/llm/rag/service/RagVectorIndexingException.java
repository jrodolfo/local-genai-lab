package net.jrodolfo.llm.rag.service;

/**
 * Exception raised when a chunk cannot be embedded during vector indexing.
 */
public class RagVectorIndexingException extends RuntimeException {

    public RagVectorIndexingException(String message) {
        super(message);
    }

    public RagVectorIndexingException(String message, Throwable cause) {
        super(message, cause);
    }
}
