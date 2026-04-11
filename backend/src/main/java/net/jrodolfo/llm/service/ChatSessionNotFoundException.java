package net.jrodolfo.llm.service;

public class ChatSessionNotFoundException extends RuntimeException {

    public ChatSessionNotFoundException(String sessionId) {
        super("Chat session not found: " + sessionId);
    }
}
