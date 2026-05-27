package net.jrodolfo.llm.client;

/**
 * Exception thrown when an error occurs during an interaction with the Ollama API.
 */
public class OllamaClientException extends RuntimeException {

    public OllamaClientException(String message) {
        super(message);
    }

    public OllamaClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
