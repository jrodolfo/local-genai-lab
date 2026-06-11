package net.jrodolfo.llm.dto;

import java.util.Map;

/**
 * Response payload for an MCP tool invocation.
 *
 * @param tool   stable MCP tool name used by the backend and UI
 * @param result structured MCP result payload returned by the local MCP server
 */
public record McpToolInvocationResponse(
        String tool,
        Map<String, Object> result
) {
}
