package net.jrodolfo.llm.client;

/**
 * Exception thrown when an error occurs during an interaction with an MCP server.
 */
public class McpClientException extends RuntimeException {

    /**
     * Constructs a new {@code McpClientException} with the specified detail message.
     *
     * @param message the detail message
     */
    public McpClientException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code McpClientException} with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public McpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
