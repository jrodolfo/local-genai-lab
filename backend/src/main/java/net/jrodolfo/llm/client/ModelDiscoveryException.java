package net.jrodolfo.llm.client;

/**
 * Exception thrown when model discovery fails.
 */
public class ModelDiscoveryException extends RuntimeException {

    public ModelDiscoveryException(String message) {
        super(message);
    }

    public ModelDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
