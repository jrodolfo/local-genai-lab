package net.jrodolfo.llm.config;

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
        return findProjectRoot().resolve(candidate).normalize();
    }

    /**
     * Traverses up the directory tree to find the project root directory.
     * The root is identified by the presence of certain markers like backend/pom.xml.
     *
     * @return the {@link Path} to the project root
     */
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

    /**
     * Checks if a candidate directory looks like the project root based on expected files.
     *
     * @param candidate the directory to check
     * @return true if the directory contains project markers
     */
    private boolean looksLikeProjectRoot(Path candidate) {
        return candidate.resolve("backend/pom.xml").toFile().isFile()
                && candidate.resolve("frontend/package.json").toFile().isFile()
                && candidate.resolve("README.md").toFile().isFile();
    }
}
