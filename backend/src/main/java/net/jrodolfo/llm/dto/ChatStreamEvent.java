package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Structured SSE event payload emitted by the streaming chat endpoint.")
public record ChatStreamEvent(
        @Schema(description = "Event type.", example = "start")
        String type,
        @Schema(description = "Active session identifier for the stream.", example = "session-123")
        String sessionId,
        @Schema(description = "Delta text for `delta` events.")
        String text,
        @Schema(description = "Tool name associated with explicit tool-phase events.", example = "aws_region_audit")
        String toolName,
        @Schema(description = "Optional tool provenance attached to the event.")
        ChatToolMetadata tool,
        @Schema(description = "Optional structured tool result payload attached to the event.")
        Map<String, Object> toolResult,
        @Schema(description = "Pending clarification state carried on the stream when more user input is needed.")
        PendingToolCallResponse pendingTool,
        @Schema(description = "Optional model provider metadata attached to the event.")
        ModelProviderMetadata metadata
) {
    public static ChatStreamEvent start(
            String sessionId,
            ChatToolMetadata tool,
            Map<String, Object> toolResult,
            PendingToolCallResponse pendingTool,
            ModelProviderMetadata metadata
    ) {
        return new ChatStreamEvent("start", sessionId, null, null, tool, toolResult, pendingTool, metadata);
    }

    public static ChatStreamEvent phase(String type, String toolName) {
        return new ChatStreamEvent(type, null, null, toolName, null, null, null, null);
    }

    public static ChatStreamEvent delta(String text) {
        return new ChatStreamEvent("delta", null, text, null, null, null, null, null);
    }

    public static ChatStreamEvent complete(
            String sessionId,
            ChatToolMetadata tool,
            Map<String, Object> toolResult,
            PendingToolCallResponse pendingTool,
            ModelProviderMetadata metadata
    ) {
        return new ChatStreamEvent("complete", sessionId, null, null, tool, toolResult, pendingTool, metadata);
    }
}
