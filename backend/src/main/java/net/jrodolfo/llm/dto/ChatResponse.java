package net.jrodolfo.llm.dto;

public record ChatResponse(
        String response,
        String model,
        ChatToolMetadata tool,
        String sessionId,
        PendingToolCallResponse pendingTool
) {
}
