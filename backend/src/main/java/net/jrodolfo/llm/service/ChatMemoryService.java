package net.jrodolfo.llm.service;

import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.model.ChatSessionMessage;
import net.jrodolfo.llm.model.PendingToolCall;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persists conversation turns to the local session store.
 *
 * <p>The service is the write side of chat memory. It appends user messages at
 * turn start, appends assistant messages at turn finish, and preserves pending
 * tool state between turns when the backend needs more user input.
 */
@Service
public class ChatMemoryService {

    private final FileChatSessionStore sessionStore;
    private final ChatSessionMetadataService chatSessionMetadataService;
    private final SessionIdPolicy sessionIdPolicy;

    /**
     * Constructs a new ChatMemoryService.
     *
     * @param sessionStore               the store for chat sessions
     * @param chatSessionMetadataService the service for enriching session metadata
     * @param sessionIdPolicy            the policy for session IDs
     */
    public ChatMemoryService(FileChatSessionStore sessionStore, ChatSessionMetadataService chatSessionMetadataService, SessionIdPolicy sessionIdPolicy) {
        this.sessionStore = sessionStore;
        this.chatSessionMetadataService = chatSessionMetadataService;
        this.sessionIdPolicy = sessionIdPolicy;
    }

    /**
     * Starts a turn by loading or creating a session and appending the user message.
     *
     * @param requestedSessionId session id from the client, or null/blank to create one
     * @param requestedModel     model requested by the client, retained for compatibility with older callers
     * @param resolvedModel      provider-resolved model stored on the session
     * @param userMessage        user message to append
     * @return in-memory session containing the new user message; not saved until the turn finishes
     */
    public ChatSession startTurn(String requestedSessionId, String requestedModel, String resolvedModel, String userMessage) {
        Instant now = Instant.now();
        String sessionId = sessionIdPolicy.requireValidOrGenerate(requestedSessionId);

        ChatSession existingSession = sessionStore.findById(sessionId)
                .map(session -> session.withUpdatedModel(resolvedModel))
                .orElseGet(() -> ChatSession.create(sessionId, resolvedModel, now));

        return existingSession.appendMessage("user", userMessage.trim(), null, null, null, null, now);
    }

    /**
     * Finishes a turn in a chat session.
     *
     * @param session          the current chat session
     * @param assistantMessage the message from the assistant
     * @param toolMetadata     metadata about the tool used, if any
     * @return the updated and saved chat session
     */
    public ChatSession finishTurn(ChatSession session, String assistantMessage, ChatToolMetadata toolMetadata) {
        return finishTurn(session, assistantMessage, toolMetadata, null, null, session.pendingToolCall());
    }

    /**
     * Finishes a turn by appending the assistant message and saving the session.
     *
     * @param session          session returned from {@link #startTurn(String, String, String, String)}
     * @param assistantMessage assistant text or clarification shown to the user
     * @param toolMetadata     tool metadata stored on the assistant message, if any
     * @param toolResult       structured tool result stored on the assistant message, if any
     * @param providerMetadata provider metadata stored on the assistant message, if any
     * @param pendingToolCall  pending tool state to preserve for the next user turn, if any
     * @return enriched and saved session
     */
    public ChatSession finishTurn(
            ChatSession session,
            String assistantMessage,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            ModelProviderMetadata providerMetadata,
            PendingToolCall pendingToolCall
    ) {
        ChatSession updatedSession = session
                .withPendingToolCall(pendingToolCall)
                .appendMessage("assistant", assistantMessage, toolMetadata, toolResult, providerMetadata, null, Instant.now());
        return sessionStore.save(chatSessionMetadataService.enrich(updatedSession));
    }

    /**
     * Retrieves the history of messages before the latest user message.
     *
     * @param session the chat session
     * @return a list of messages before the latest user message
     */
    public List<ChatSessionMessage> historyBeforeLatestUserMessage(ChatSession session) {
        List<ChatSessionMessage> messages = session.messages();
        if (messages.isEmpty()) {
            return List.of();
        }
        return messages.subList(0, messages.size() - 1);
    }
}
