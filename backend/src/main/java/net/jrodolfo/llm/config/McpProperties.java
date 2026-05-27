package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration properties for Model Context Protocol (MCP) tooling.
 * Enables integration with external tools via the MCP protocol.
 * 
 * @param enabled whether MCP tooling is enabled
 * @param command the command to execute the MCP server
 * @param args arguments for the MCP server command
 * @param workingDirectory the working directory for the MCP server
 * @param startupTimeoutSeconds timeout in seconds for MCP server startup
 * @param toolTimeoutSeconds timeout in seconds for MCP tool execution
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
    public Path resolvedWorkingDirectory() {
        return resolveAgainstProjectRoot(workingDirectory);
    }

    private Path resolveAgainstProjectRoot(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return findProjectRoot();
        }

        Path candidate = Path.of(configuredPath);
        if (candidate.isAbsolute()) {
            return candidate.toAbsolutePath().normalize();
        }
        return findProjectRoot().resolve(candidate).normalize();
    }

    private Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        while (current != null) {
            if (looksLikeProjectRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    private boolean looksLikeProjectRoot(Path candidate) {
        return candidate.resolve("backend/pom.xml").toFile().isFile()
                && candidate.resolve("frontend/package.json").toFile().isFile()
                && candidate.resolve("README.md").toFile().isFile();
    }
}
