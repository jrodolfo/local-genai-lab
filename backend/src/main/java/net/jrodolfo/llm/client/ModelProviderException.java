package net.jrodolfo.llm.client;

/**
 * Exception thrown when an error occurs during an interaction with a model provider.
 */
public class ModelProviderException extends RuntimeException {

    public ModelProviderException(String message) {
        super(message);
    }

    public ModelProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
