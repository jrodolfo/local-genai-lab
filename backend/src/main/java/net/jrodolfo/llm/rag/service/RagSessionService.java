package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.model.ChatRagSourceChunk;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.service.ChatSessionMetadataService;
import net.jrodolfo.llm.service.FileChatSessionStore;
import net.jrodolfo.llm.service.SessionIdPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RagSessionService {

    private final FileChatSessionStore sessionStore;
    private final ChatSessionMetadataService chatSessionMetadataService;
    private final SessionIdPolicy sessionIdPolicy;

    public RagSessionService(
            FileChatSessionStore sessionStore,
            ChatSessionMetadataService chatSessionMetadataService,
            SessionIdPolicy sessionIdPolicy
    ) {
        this.sessionStore = sessionStore;
        this.chatSessionMetadataService = chatSessionMetadataService;
        this.sessionIdPolicy = sessionIdPolicy;
    }

    public ChatSession startTurn(String requestedSessionId, String resolvedModel, String question) {
        Instant now = Instant.now();
        String sessionId = sessionIdPolicy.requireValidOrGenerate(requestedSessionId);

        ChatSession existingSession = sessionStore.findById(sessionId)
                .map(session -> {
                    if (!"rag".equals(session.mode())) {
                        throw new IllegalArgumentException("Requested session is not a RAG session.");
                    }
                    return session.withUpdatedModel(resolvedModel);
                })
                .orElseGet(() -> ChatSession.create(sessionId, resolvedModel, now, "rag"));

        return existingSession.appendMessage("user", question.trim(), null, null, null, null, now);
    }

    public ChatSession finishTurn(
            ChatSession session,
            String answer,
            ModelProviderMetadata providerMetadata,
            List<ChatRagSourceChunk> ragSources
    ) {
        ChatSession updatedSession = session.appendMessage(
                "assistant",
                answer,
                null,
                null,
                providerMetadata,
                ragSources,
                Instant.now()
        );
        return sessionStore.save(chatSessionMetadataService.enrich(updatedSession));
    }
}
