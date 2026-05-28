package net.jrodolfo.llm.dto;

import java.util.Map;

/**
 * Response payload for an MCP tool invocation.
 */
public record McpToolInvocationResponse(
        String tool,
        Map<String, Object> result
) {
}
