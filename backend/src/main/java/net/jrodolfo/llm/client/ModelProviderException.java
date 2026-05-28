package net.jrodolfo.llm.client;

/**
 * Exception thrown when an error occurs during an interaction with a model provider.
 */
public class ModelProviderException extends RuntimeException {

    /**
     * Constructs a new {@code ModelProviderException} with the specified detail message.
     *
     * @param message the detail message
     */
    public ModelProviderException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ModelProviderException} with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public ModelProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
