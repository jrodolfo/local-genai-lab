package net.jrodolfo.llm.health;

import net.jrodolfo.llm.client.McpClient;
import net.jrodolfo.llm.config.McpProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Health indicator for the Model Context Protocol (MCP) integration.
 * <p>
 * Checks if the MCP tool is enabled and if the configured working directory
 * and entrypoint exist.
 */
@Component("mcp")
public class McpHealthIndicator implements HealthIndicator {

    private final McpProperties mcpProperties;

    /**
     * Constructs a new McpHealthIndicator with the specified properties and client.
     *
     * @param mcpProperties the properties for MCP configuration.
     * @param mcpClient     the client for MCP interactions (not directly used in health check but injected for readiness).
     */
    public McpHealthIndicator(McpProperties mcpProperties, McpClient mcpClient) {
        this.mcpProperties = mcpProperties;
    }

    /**
     * Performs the health check for the MCP integration.
     *
     * @return the health status and details of the MCP integration.
     */
    @Override
    public Health health() {
        boolean commandConfigured = mcpProperties.command() != null && !mcpProperties.command().isBlank();
        Path workingDirectory = mcpProperties.resolvedWorkingDirectory();
        boolean workingDirectoryExists = Files.isDirectory(workingDirectory);
        String entrypoint = mcpProperties.args() == null || mcpProperties.args().isEmpty() ? "" : mcpProperties.args().getFirst();
        Path entrypointPath = entrypoint.isBlank() ? workingDirectory : workingDirectory.resolve(entrypoint).normalize();
        boolean entrypointExists = Files.exists(entrypointPath);

        if (!mcpProperties.enabled()) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("status", "disabled")
                    .withDetail("command", mcpProperties.command())
                    .withDetail("commandConfigured", commandConfigured)
                    .withDetail("workingDirectory", workingDirectory.toString())
                    .withDetail("workingDirectoryExists", workingDirectoryExists)
                    .withDetail("entrypoint", entrypointPath.toString())
                    .withDetail("entrypointExists", entrypointExists)
                    .build();
        }

        if (!commandConfigured || !workingDirectoryExists || !entrypointExists) {
            return Health.down()
                    .withDetail("enabled", true)
                    .withDetail("status", "misconfigured")
                    .withDetail("command", mcpProperties.command())
                    .withDetail("commandConfigured", commandConfigured)
                    .withDetail("workingDirectory", workingDirectory.toString())
                    .withDetail("workingDirectoryExists", workingDirectoryExists)
                    .withDetail("entrypoint", entrypointPath.toString())
                    .withDetail("entrypointExists", entrypointExists)
                    .build();
        }

        return Health.up()
                .withDetail("enabled", true)
                .withDetail("status", "ready")
                .withDetail("command", mcpProperties.command())
                .withDetail("commandConfigured", true)
                .withDetail("workingDirectory", workingDirectory.toString())
                .withDetail("workingDirectoryExists", true)
                .withDetail("entrypoint", entrypointPath.toString())
                .withDetail("entrypointExists", true)
                .withDetail("toolTimeoutSeconds", mcpProperties.toolTimeoutSeconds())
                .withDetail("startupTimeoutSeconds", mcpProperties.startupTimeoutSeconds())
                .withDetail("runnable", true)
                .withDetail("probe", "config-only")
                .build();
    }
}
