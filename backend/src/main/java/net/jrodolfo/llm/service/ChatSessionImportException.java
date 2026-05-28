package net.jrodolfo.llm.service;

/**
 * Exception thrown when an error occurs while importing a chat session.
 */
public class ChatSessionImportException extends RuntimeException {

    /**
     * Constructs a new ChatSessionImportException with the specified detail message.
     *
     * @param message the detail message
     */
    public ChatSessionImportException(String message) {
        super(message);
    }

    /**
     * Constructs a new ChatSessionImportException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public ChatSessionImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
