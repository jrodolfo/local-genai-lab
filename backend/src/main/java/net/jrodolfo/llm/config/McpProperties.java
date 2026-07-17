package net.jrodolfo.llm.config;

import net.jrodolfo.llm.util.PathUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration properties for Model Context Protocol (MCP) tooling.
 * Enables integration with external tools via the MCP protocol.
 *
 * @param enabled               whether MCP tooling is enabled
 * @param command               the command to execute the MCP server
 * @param args                  arguments for the MCP server command
 * @param workingDirectory      the working directory for the MCP server
 * @param startupTimeoutSeconds timeout in seconds for MCP server startup
 * @param toolTimeoutSeconds    timeout in seconds for MCP tool execution
 */
@ConfigurationProperties(prefix = "mcp")
public record McpProperties(
        boolean enabled,
        String command,
        List<String> args,
        String workingDirectory,
        int startupTimeoutSeconds,
        int toolTimeoutSeconds
) {
    /**
     * Resolves the MCP working directory against the project root.
     *
     * @return the resolved path to the MCP working directory
     */
    public Path resolvedWorkingDirectory() {
        return resolveAgainstProjectRoot(workingDirectory);
    }

    /**
     * Resolves a configured path against the project root if it is not already absolute.
     * If the path is null or blank, the project root itself is returned.
     *
     * @param configuredPath the path string to resolve
     * @return the resolved {@link Path}
     */
    private Path resolveAgainstProjectRoot(String configuredPath) {
        return PathUtils.resolveAgainstProjectRoot(configuredPath);
    }
}
