package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "mcp")
public record McpProperties(
        boolean enabled,
        String command,
        List<String> args,
        String workingDirectory,
        int startupTimeoutSeconds,
        int toolTimeoutSeconds
) {
}
