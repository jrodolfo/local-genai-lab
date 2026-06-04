package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.RagRetrievalMetadata;
import net.jrodolfo.llm.dto.RagTimingMetadata;
import net.jrodolfo.llm.model.ChatRagSourceChunk;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.service.ChatSessionMetadataService;
import net.jrodolfo.llm.service.FileChatSessionStore;
import net.jrodolfo.llm.service.SessionIdPolicy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing chat sessions in the context of RAG.
 * It handles starting and finishing conversation turns and persists them in the session store.
 */
@Service
public class RagSessionService {

    private final FileChatSessionStore sessionStore;
    private final ChatSessionMetadataService chatSessionMetadataService;
    private final SessionIdPolicy sessionIdPolicy;

    /**
     * Constructs a new RagSessionService.
     *
     * @param sessionStore               the store used for persisting chat sessions
     * @param chatSessionMetadataService the service used for enriching session metadata
     * @param sessionIdPolicy            the policy used for generating and validating session IDs
     */
    public RagSessionService(
            FileChatSessionStore sessionStore,
            ChatSessionMetadataService chatSessionMetadataService,
            SessionIdPolicy sessionIdPolicy
    ) {
        this.sessionStore = sessionStore;
        this.chatSessionMetadataService = chatSessionMetadataService;
        this.sessionIdPolicy = sessionIdPolicy;
    }

    /**
     * Starts a new turn in a chat session.
     *
     * @param requestedSessionId the ID requested by the user, or null/empty to generate a new one
     * @param resolvedModel      the resolved name of the model being used
     * @param question           the user's question
     * @return the {@link ChatSession} with the new user message appended
     */
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

    /**
     * Finishes a chat turn by appending the assistant's answer and source metadata, then saving the session.
     *
     * @param session          the current chat session
     * @param answer           the assistant's generated answer
     * @param providerMetadata metadata from the LLM provider
     * @param ragSources       the source chunks used to generate the answer
     * @return the updated and persisted {@link ChatSession}
     */
    public ChatSession finishTurn(
            ChatSession session,
            String answer,
            ModelProviderMetadata providerMetadata,
            List<ChatRagSourceChunk> ragSources
    ) {
        return finishTurn(session, answer, providerMetadata, ragSources, null);
    }

    public ChatSession finishTurn(
            ChatSession session,
            String answer,
            ModelProviderMetadata providerMetadata,
            List<ChatRagSourceChunk> ragSources,
            RagRetrievalMetadata ragRetrieval
    ) {
        return finishTurn(session, answer, providerMetadata, ragSources, ragRetrieval, null);
    }

    public ChatSession finishTurn(
            ChatSession session,
            String answer,
            ModelProviderMetadata providerMetadata,
            List<ChatRagSourceChunk> ragSources,
            RagRetrievalMetadata ragRetrieval,
            RagTimingMetadata ragTiming
    ) {
        ChatSession updatedSession = session.appendMessage(
                "assistant",
                answer,
                null,
                null,
                providerMetadata,
                ragSources,
                ragRetrieval,
                ragTiming,
                Instant.now()
        );
        return sessionStore.save(chatSessionMetadataService.enrich(updatedSession));
    }
}
