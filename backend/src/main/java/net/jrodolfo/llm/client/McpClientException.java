package net.jrodolfo.llm.client;

/**
 * Exception thrown when an error occurs during an interaction with an MCP server.
 */
public class McpClientException extends RuntimeException {

    public McpClientException(String message) {
        super(message);
    }

    public McpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
