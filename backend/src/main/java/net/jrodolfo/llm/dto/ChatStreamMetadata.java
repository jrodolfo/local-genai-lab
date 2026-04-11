package net.jrodolfo.llm.dto;

public record ChatStreamMetadata(
        String sessionId,
        ChatToolMetadata tool
) {
}
