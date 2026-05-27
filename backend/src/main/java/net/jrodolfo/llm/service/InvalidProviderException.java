package net.jrodolfo.llm.service;

/**
 * Exception thrown when an invalid model provider is specified.
 */
public class InvalidProviderException extends RuntimeException {

    /**
     * Constructs a new InvalidProviderException with the specified detail message.
     *
     * @param message the detail message
     */
    public InvalidProviderException(String message) {
        super(message);
    }
}
