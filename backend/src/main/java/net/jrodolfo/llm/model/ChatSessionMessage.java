package net.jrodolfo.llm.model;

import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a single message within a chat session.
 *
 * @param role       the role of the message sender (e.g., "user", "assistant", "system")
 * @param content    the text content of the message
 * @param tool       metadata about a tool call, if the message involves one
 * @param toolResult the result of a tool execution, if applicable
 * @param metadata   metadata from the model provider (e.g., token usage)
 * @param ragSources a list of RAG source chunks that informed this message
 * @param timestamp  the timestamp when the message was created
 */
public record ChatSessionMessage(
        String role,
        String content,
        ChatToolMetadata tool,
        Map<String, Object> toolResult,
        ModelProviderMetadata metadata,
        List<ChatRagSourceChunk> ragSources,
        Instant timestamp
) {
}
