package net.jrodolfo.llm.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        boolean enabled,
        String corpusRoot,
        int maxChunkSize,
        int chunkOverlap,
        int topK,
        String retrievalMode
) {

    public Path resolvedCorpusRoot() {
        Path candidate = Path.of(corpusRoot);
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
