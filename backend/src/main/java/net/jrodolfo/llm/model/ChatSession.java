package net.jrodolfo.llm.model;

import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.RagRetrievalMetadata;
import net.jrodolfo.llm.dto.RagTimingMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a chat session containing a sequence of messages and session metadata.
 *
 * @param sessionId       the unique identifier for the session
 * @param model           the identifier of the LLM model being used
 * @param createdAt       the timestamp when the session was created
 * @param updatedAt       the timestamp when the session was last updated
 * @param messages        the list of messages in this session
 * @param pendingToolCall any tool call that is waiting for user confirmation or more information
 * @param title           the title of the chat session
 * @param summary         a summary of the chat conversation
 * @param mode            the operational mode of the session (e.g., "chat")
 */
public record ChatSession(
        String sessionId,
        String model,
        Instant createdAt,
        Instant updatedAt,
        List<ChatSessionMessage> messages,
        PendingToolCall pendingToolCall,
        String title,
        String summary,
        String mode
) {
    /**
     * Compact constructor to ensure messages list is mutable and mode has a default value.
     */
    public ChatSession {
        messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        mode = (mode == null || mode.isBlank()) ? "chat" : mode;
    }

    /**
     * Creates a new ChatSession with an updated model name.
     *
     * @param resolvedModel the new model identifier
     * @return a new ChatSession instance
     */
    public ChatSession withUpdatedModel(String resolvedModel) {
        return new ChatSession(sessionId, resolvedModel, createdAt, updatedAt, messages, pendingToolCall, title, summary, mode);
    }

    /**
     * Appends a new message to the session and updates the {@code updatedAt} timestamp.
     *
     * @param role             the role of the message sender (e.g., "user", "assistant")
     * @param content          the text content of the message
     * @param toolMetadata     metadata about a tool call, if any
     * @param toolResult       the result of a tool execution, if any
     * @param providerMetadata metadata about the model provider
     * @param ragSources       any RAG sources associated with this message
     * @param timestamp        the timestamp of the message
     * @return a new ChatSession instance with the message appended
     */
    public ChatSession appendMessage(
            String role,
            String content,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            ModelProviderMetadata providerMetadata,
            List<ChatRagSourceChunk> ragSources,
            Instant timestamp
    ) {
        return appendMessage(role, content, toolMetadata, toolResult, providerMetadata, ragSources, null, null, timestamp);
    }

    /**
     * Appends a new message to the session and updates the {@code updatedAt} timestamp.
     *
     * @param role             the role of the message sender (e.g., "user", "assistant")
     * @param content          the text content of the message
     * @param toolMetadata     metadata about a tool call, if any
     * @param toolResult       the result of a tool execution, if any
     * @param providerMetadata metadata about the model provider
     * @param ragSources       any RAG sources associated with this message
     * @param ragRetrieval     retrieval target metadata for a RAG answer
     * @param ragTiming        backend timing metadata for a RAG answer
     * @param timestamp        the timestamp of the message
     * @return a new ChatSession instance with the message appended
     */
    public ChatSession appendMessage(
            String role,
            String content,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            ModelProviderMetadata providerMetadata,
            List<ChatRagSourceChunk> ragSources,
            RagRetrievalMetadata ragRetrieval,
            RagTimingMetadata ragTiming,
            Instant timestamp
    ) {
        List<ChatSessionMessage> updatedMessages = new ArrayList<>(messages);
        updatedMessages.add(new ChatSessionMessage(role, content, toolMetadata, toolResult, providerMetadata, ragSources, ragRetrieval, ragTiming, timestamp));
        return new ChatSession(sessionId, model, createdAt, timestamp, updatedMessages, pendingToolCall, title, summary, mode);
    }

    /**
     * Creates a new ChatSession with the specified pending tool call.
     *
     * @param pendingToolCall the pending tool call to associate with the session
     * @return a new ChatSession instance
     */
    public ChatSession withPendingToolCall(PendingToolCall pendingToolCall) {
        return new ChatSession(sessionId, model, createdAt, updatedAt, messages, pendingToolCall, title, summary, mode);
    }

    /**
     * Creates a new ChatSession with updated title and summary.
     *
     * @param title   the new title
     * @param summary the new summary
     * @return a new ChatSession instance
     */
    public ChatSession withMetadata(String title, String summary) {
        return new ChatSession(sessionId, model, createdAt, updatedAt, messages, pendingToolCall, title, summary, mode);
    }

    /**
     * Factory method to create a new ChatSession with default mode.
     *
     * @param sessionId the unique identifier for the session
     * @param model     the model identifier
     * @param now       the current timestamp
     * @return a new ChatSession instance
     */
    public static ChatSession create(String sessionId, String model, Instant now) {
        return create(sessionId, model, now, "chat");
    }

    /**
     * Factory method to create a new ChatSession.
     *
     * @param sessionId the unique identifier for the session
     * @param model     the model identifier
     * @param now       the current timestamp
     * @param mode      the operational mode
     * @return a new ChatSession instance
     */
    public static ChatSession create(String sessionId, String model, Instant now, String mode) {
        return new ChatSession(sessionId, model, now, now, List.of(), null, null, null, mode);
    }
}
