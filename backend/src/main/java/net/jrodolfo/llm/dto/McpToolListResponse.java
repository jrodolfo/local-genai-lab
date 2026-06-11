package net.jrodolfo.llm.dto;

import java.util.List;
import java.util.Map;

/**
 * Response payload listing available MCP tools.
 *
 * @param enabled whether MCP integration is enabled in the running backend
 * @param tools   tool descriptors reported by the MCP server when enabled
 */
public record McpToolListResponse(
        boolean enabled,
        List<McpToolResponse> tools
) {
    /**
     * Details of an individual MCP tool.
     *
     * @param name        stable MCP tool name
     * @param title       human-readable title from the MCP server
     * @param description human-readable description from the MCP server
     * @param inputSchema JSON schema describing the tool input contract
     */
    public record McpToolResponse(
            String name,
            String title,
            String description,
            Map<String, Object> inputSchema
    ) {
    }
}
