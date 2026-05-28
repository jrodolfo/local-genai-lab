package net.jrodolfo.llm.client;

/**
 * Exception thrown when an error occurs during an interaction with the Ollama API.
 */
public class OllamaClientException extends RuntimeException {

    /**
     * Constructs a new {@code OllamaClientException} with the specified detail message.
     *
     * @param message the detail message
     */
    public OllamaClientException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code OllamaClientException} with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public OllamaClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
