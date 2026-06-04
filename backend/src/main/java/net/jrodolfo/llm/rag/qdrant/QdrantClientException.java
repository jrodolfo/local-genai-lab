package net.jrodolfo.llm.rag.qdrant;

/**
 * Exception raised when the Qdrant HTTP boundary cannot complete an operation.
 */
public class QdrantClientException extends RuntimeException {

    public QdrantClientException(String message) {
        super(message);
    }

    public QdrantClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
