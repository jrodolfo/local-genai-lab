package net.jrodolfo.llm.service;

/**
 * Exception thrown when an invalid session ID is provided.
 */
public class InvalidSessionIdException extends RuntimeException {

    /**
     * Constructs a new InvalidSessionIdException with the specified detail message.
     *
     * @param message the detail message
     */
    public InvalidSessionIdException(String message) {
        super(message);
    }
}
