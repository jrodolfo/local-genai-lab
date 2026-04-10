package net.jrodolfo.llm.dto;

import java.util.List;
import java.util.Map;

public record McpToolListResponse(
        boolean enabled,
        List<McpToolResponse> tools
) {
    public record McpToolResponse(
            String name,
            String title,
            String description,
            Map<String, Object> inputSchema
    ) {
    }
}
