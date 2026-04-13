package net.jrodolfo.llm.client;

public class ModelDiscoveryException extends RuntimeException {

    public ModelDiscoveryException(String message) {
        super(message);
    }

    public ModelDiscoveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
