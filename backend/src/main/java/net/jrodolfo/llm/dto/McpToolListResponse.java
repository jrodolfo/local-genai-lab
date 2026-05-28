package net.jrodolfo.llm.dto;

import java.util.List;
import java.util.Map;

/**
 * Response payload listing available MCP tools.
 */
public record McpToolListResponse(
        boolean enabled,
        List<McpToolResponse> tools
) {
    /**
     * Details of an individual MCP tool.
     */
    public record McpToolResponse(
            String name,
            String title,
            String description,
            Map<String, Object> inputSchema
    ) {
    }
}
