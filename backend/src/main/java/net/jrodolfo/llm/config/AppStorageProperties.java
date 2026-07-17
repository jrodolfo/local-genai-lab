package net.jrodolfo.llm.config;

import net.jrodolfo.llm.util.PathUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Configuration properties for application storage.
 * Handles resolution of directories for sessions and reports relative to the project root.
 *
 * @param sessionsDirectory the directory path for storing session data
 * @param reportsDirectory  the directory path for storing reports
 */
@ConfigurationProperties(prefix = "app.storage")
public record AppStorageProperties(
        String sessionsDirectory,
        String reportsDirectory
) {
    /**
     * Resolves the sessions directory against the project root.
     *
     * @return the resolved path to the sessions directory
     */
    public Path resolvedSessionsDirectory() {
        return resolveAgainstProjectRoot(sessionsDirectory);
    }

    /**
     * Resolves the reports directory against the project root.
     *
     * @return the resolved path to the reports directory
     */
    public Path resolvedReportsDirectory() {
        return resolveAgainstProjectRoot(reportsDirectory);
    }

    /**
     * Resolves a configured path against the project root if it is not already absolute.
     *
     * @param configuredPath the path string to resolve
     * @return the resolved {@link Path}
     */
    private Path resolveAgainstProjectRoot(String configuredPath) {
        Path candidate = Path.of(configuredPath);
        if (candidate.isAbsolute()) {
            return candidate.toAbsolutePath().normalize();
        }
        return PathUtils.resolveAgainstProjectRoot(configuredPath);
    }
}
