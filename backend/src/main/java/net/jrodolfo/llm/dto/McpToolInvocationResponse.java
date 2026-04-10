package net.jrodolfo.llm.dto;

import java.util.Map;

public record McpToolInvocationResponse(
        String tool,
        Map<String, Object> result
) {
}
