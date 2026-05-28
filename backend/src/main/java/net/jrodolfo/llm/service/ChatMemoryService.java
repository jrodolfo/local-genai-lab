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
 * Service for managing chat session memory, including starting and finishing turns.
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
     * Starts a new turn in a chat session.
     *
     * @param requestedSessionId the session ID requested by the client
     * @param requestedModel     the model name requested by the client
     * @param resolvedModel      the actual model ID to be used
     * @param userMessage        the message from the user
     * @return the updated chat session
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
     * Finishes a turn in a chat session with full details.
     *
     * @param session          the current chat session
     * @param assistantMessage the message from the assistant
     * @param toolMetadata     metadata about the tool used, if any
     * @param toolResult       the result of the tool invocation, if any
     * @param providerMetadata metadata from the model provider, if any
     * @param pendingToolCall  the pending tool call, if any
     * @return the updated and saved chat session
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
