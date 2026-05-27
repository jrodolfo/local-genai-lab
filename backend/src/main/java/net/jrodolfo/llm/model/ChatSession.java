package net.jrodolfo.llm.model;

import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public ChatSession {
        messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
        mode = (mode == null || mode.isBlank()) ? "chat" : mode;
    }

    public ChatSession withUpdatedModel(String resolvedModel) {
        return new ChatSession(sessionId, resolvedModel, createdAt, updatedAt, messages, pendingToolCall, title, summary, mode);
    }

    public ChatSession appendMessage(
            String role,
            String content,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            ModelProviderMetadata providerMetadata,
            List<ChatRagSourceChunk> ragSources,
            Instant timestamp
    ) {
        List<ChatSessionMessage> updatedMessages = new ArrayList<>(messages);
        updatedMessages.add(new ChatSessionMessage(role, content, toolMetadata, toolResult, providerMetadata, ragSources, timestamp));
        return new ChatSession(sessionId, model, createdAt, timestamp, updatedMessages, pendingToolCall, title, summary, mode);
    }

    public ChatSession withPendingToolCall(PendingToolCall pendingToolCall) {
        return new ChatSession(sessionId, model, createdAt, updatedAt, messages, pendingToolCall, title, summary, mode);
    }

    public ChatSession withMetadata(String title, String summary) {
        return new ChatSession(sessionId, model, createdAt, updatedAt, messages, pendingToolCall, title, summary, mode);
    }

    public static ChatSession create(String sessionId, String model, Instant now) {
        return create(sessionId, model, now, "chat");
    }

    public static ChatSession create(String sessionId, String model, Instant now, String mode) {
        return new ChatSession(sessionId, model, now, now, List.of(), null, null, null, mode);
    }
}
