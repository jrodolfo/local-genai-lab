package net.jrodolfo.llm.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.nio.file.Path;
import java.util.List;

/**
 * Configuration properties for the RAG (Retrieval-Augmented Generation) system.
 *
 * @param enabled       Whether the RAG system is enabled.
 * @param corpusRoot    The root directory containing the document corpus.
 * @param maxChunkSize  The maximum size of a text chunk in characters.
 * @param chunkOverlap  The number of overlapping characters between consecutive chunks.
 * @param topK          The number of top relevant chunks to retrieve for each query.
 * @param retrievalMode     The backend default retrieval mode when a request does not choose a target.
 * @param vectorStore       The backend default vector store when vector retrieval is selected.
 * @param qdrantUrl         The Qdrant endpoint used when the selected vector store is Qdrant.
 * @param qdrantCollection  The Qdrant collection used when the selected vector store is Qdrant.
 * @param embeddingProvider The embedding runtime used by vector retrieval.
 * @param embeddingModel    The embedding model used by vector retrieval.
 * @param excludedSourcePaths Relative corpus paths that should not be indexed.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        boolean enabled,
        String corpusRoot,
        int maxChunkSize,
        int chunkOverlap,
        int topK,
        String retrievalMode,
        String vectorStore,
        String qdrantUrl,
        String qdrantCollection,
        String embeddingProvider,
        String embeddingModel,
        List<String> excludedSourcePaths
) {
    @ConstructorBinding
    public RagProperties {
        vectorStore = isBlank(vectorStore) ? "in-memory" : vectorStore;
        qdrantUrl = isBlank(qdrantUrl) ? "http://localhost:6333" : qdrantUrl;
        qdrantCollection = isBlank(qdrantCollection) ? "local_genai_lab_docs" : qdrantCollection;
        excludedSourcePaths = excludedSourcePaths == null ? List.of() : List.copyOf(excludedSourcePaths);
    }

    public RagProperties(
            boolean enabled,
            String corpusRoot,
            int maxChunkSize,
            int chunkOverlap,
            int topK,
            String retrievalMode,
            String embeddingProvider,
            String embeddingModel
    ) {
        this(
                enabled,
                corpusRoot,
                maxChunkSize,
                chunkOverlap,
                topK,
                retrievalMode,
                "in-memory",
                "http://localhost:6333",
                "local_genai_lab_docs",
                embeddingProvider,
                embeddingModel,
                List.of()
        );
    }

    public RagProperties(
            boolean enabled,
            String corpusRoot,
            int maxChunkSize,
            int chunkOverlap,
            int topK,
            String retrievalMode,
            String embeddingProvider,
            String embeddingModel,
            List<String> excludedSourcePaths
    ) {
        this(
                enabled,
                corpusRoot,
                maxChunkSize,
                chunkOverlap,
                topK,
                retrievalMode,
                "in-memory",
                "http://localhost:6333",
                "local_genai_lab_docs",
                embeddingProvider,
                embeddingModel,
                excludedSourcePaths
        );
    }

    public RagProperties(
            boolean enabled,
            String corpusRoot,
            int maxChunkSize,
            int chunkOverlap,
            int topK,
            String retrievalMode,
            String vectorStore,
            String qdrantUrl,
            String qdrantCollection,
            String embeddingProvider,
            String embeddingModel
    ) {
        this(
                enabled,
                corpusRoot,
                maxChunkSize,
                chunkOverlap,
                topK,
                retrievalMode,
                vectorStore,
                qdrantUrl,
                qdrantCollection,
                embeddingProvider,
                embeddingModel,
                List.of()
        );
    }

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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
