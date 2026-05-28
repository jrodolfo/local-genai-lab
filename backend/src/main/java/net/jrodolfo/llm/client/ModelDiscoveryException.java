package net.jrodolfo.llm.client;

/**
 * Exception thrown when model discovery fails.
 */
public class ModelDiscoveryException extends RuntimeException {

    /**
     * Constructs a new {@code ModelDiscoveryException} with the specified detail message.
     *
     * @param message the detail message
     */
    public ModelDiscoveryException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ModelDiscoveryException} with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public ModelDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
