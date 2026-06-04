package net.jrodolfo.llm.rag.service;

/**
 * Exception raised when vector retrieval cannot embed or search a query.
 */
public class RagVectorRetrievalException extends RuntimeException {

    public RagVectorRetrievalException(String message) {
        super(message);
    }

    public RagVectorRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
