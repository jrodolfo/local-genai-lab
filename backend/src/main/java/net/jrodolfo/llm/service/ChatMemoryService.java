package net.jrodolfo.llm.service;

import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.model.ChatSessionMessage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChatMemoryService {

    private final FileChatSessionStore sessionStore;

    public ChatMemoryService(FileChatSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public ChatSession startTurn(String requestedSessionId, String requestedModel, String resolvedModel, String userMessage) {
        Instant now = Instant.now();
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedSessionId.trim();

        ChatSession existingSession = sessionStore.findById(sessionId)
                .map(session -> session.withUpdatedModel(resolvedModel))
                .orElseGet(() -> ChatSession.create(sessionId, resolvedModel, now));

        return existingSession.appendMessage("user", userMessage.trim(), null, now);
    }

    public ChatSession finishTurn(ChatSession session, String assistantMessage, ChatToolMetadata toolMetadata) {
        ChatSession updatedSession = session.appendMessage("assistant", assistantMessage, toolMetadata, Instant.now());
        return sessionStore.save(updatedSession);
    }

    public List<ChatSessionMessage> historyBeforeLatestUserMessage(ChatSession session) {
        List<ChatSessionMessage> messages = session.messages();
        if (messages.isEmpty()) {
            return List.of();
        }
        return messages.subList(0, messages.size() - 1);
    }
}
