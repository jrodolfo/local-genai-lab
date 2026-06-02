package net.jrodolfo.llm.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Configuration properties for the RAG (Retrieval-Augmented Generation) system.
 *
 * @param enabled       Whether the RAG system is enabled.
 * @param corpusRoot    The root directory containing the document corpus.
 * @param maxChunkSize  The maximum size of a text chunk in characters.
 * @param chunkOverlap  The number of overlapping characters between consecutive chunks.
 * @param topK          The number of top relevant chunks to retrieve for each query.
 * @param retrievalMode     The mode of retrieval to use.
 * @param embeddingProvider The embedding runtime to use for future vector retrieval.
 * @param embeddingModel    The embedding model to use for future vector retrieval.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        boolean enabled,
        String corpusRoot,
        int maxChunkSize,
        int chunkOverlap,
        int topK,
        String retrievalMode,
        String embeddingProvider,
        String embeddingModel
) {

    /**
     * Resolves the corpus root path. If the configured path is relative, it is resolved
     * against the project root.
     *
     * @return The absolute and normalized Path to the corpus root.
     */
    public Path resolvedCorpusRoot() {
        Path candidate = Path.of(corpusRoot);
        if (candidate.isAbsolute()) {
            return candidate.toAbsolutePath().normalize();
        }
        return findProjectRoot().resolve(candidate).normalize();
    }

    /**
     * Finds the project root directory by searching upwards from the current working directory.
     * Looks for markers like 'backend/pom.xml' and 'frontend/package.json'.
     *
     * @return The Path to the project root directory, or the current directory if not found.
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
     * Checks if the given directory looks like the project root based on its contents.
     *
     * @param candidate The directory to check.
     * @return true if the directory contains the expected project structure, false otherwise.
     */
    private boolean looksLikeProjectRoot(Path candidate) {
        return candidate.resolve("backend/pom.xml").toFile().isFile()
                && candidate.resolve("frontend/package.json").toFile().isFile()
                && candidate.resolve("README.md").toFile().isFile();
    }
}
