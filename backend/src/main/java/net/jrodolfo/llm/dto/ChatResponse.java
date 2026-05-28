package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Chat response for non-streaming chat requests.
 */
@Schema(description = "Chat response for non-streaming chat requests.")
public record ChatResponse(
        @Schema(description = "Assistant response text.")
        String response,
        @Schema(description = "Resolved model that handled the request.", example = "llama3:8b")
        String model,
        @Schema(description = "Optional tool provenance when a local MCP-backed tool was used.")
        ChatToolMetadata tool,
        @Schema(description = "Optional structured tool result payload for supported MCP report flows.")
        Map<String, Object> toolResult,
        @Schema(description = "Active session identifier after the request is processed.", example = "session-123")
        String sessionId,
        @Schema(description = "Pending clarification state when a follow-up message is required before a tool can run.")
        PendingToolCallResponse pendingTool,
        @Schema(description = "Optional model provider metadata such as provider name, token usage, and latency.")
        ModelProviderMetadata metadata
) {
}
