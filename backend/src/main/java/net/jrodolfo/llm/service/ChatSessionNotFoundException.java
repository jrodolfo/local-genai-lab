package net.jrodolfo.llm.service;

/**
 * Exception thrown when a requested chat session cannot be found.
 */
public class ChatSessionNotFoundException extends RuntimeException {

    /**
     * Constructs a new ChatSessionNotFoundException.
     *
     * @param sessionId the ID of the session that was not found
     */
    public ChatSessionNotFoundException(String sessionId) {
        super("Chat session not found: " + sessionId);
    }
}
