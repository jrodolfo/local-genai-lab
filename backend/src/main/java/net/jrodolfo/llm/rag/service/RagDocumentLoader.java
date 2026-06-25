package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.model.RagDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service responsible for loading documents from the file system.
 * Currently supports loading and parsing Markdown files.
 */
@Service
public class RagDocumentLoader {

    /**
     * Recursively loads all Markdown files from the specified corpus root directory.
     *
     * @param corpusRoot the root directory containing the RAG corpus
     * @return a list of {@link RagDocument} objects
     * @throws IllegalStateException if the root directory is invalid or if an error occurs during loading
     */
    public List<RagDocument> loadMarkdownDocuments(Path corpusRoot) {
        if (!Files.isDirectory(corpusRoot)) {
            throw new IllegalStateException("RAG corpus root does not exist: " + corpusRoot);
        }
        try (Stream<Path> stream = Files.walk(corpusRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.naturalOrder())
                    .map(path -> toDocument(corpusRoot, path))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load RAG corpus from " + corpusRoot, ex);
        }
    }

    /**
     * Converts a file path to a {@link RagDocument}.
     *
     * @param corpusRoot The root directory of the corpus.
     * @param file       The file to load.
     * @return The loaded {@link RagDocument}.
     * @throws IllegalStateException if the file cannot be read.
     */
    private RagDocument toDocument(Path corpusRoot, Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String title = markdownTitle(content);
            String relativePath = corpusRoot.relativize(file).toString().replace('\\', '/');
            return new RagDocument(Path.of(relativePath), title != null ? title : relativePath, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read RAG document " + file, ex);
        }
    }

    /**
     * Extracts a useful Markdown title from the content.
     * <p>Some older ADRs use {@code # Title} as a field label and put the real
     * title on the next line. In that case, the next non-empty non-heading line
     * is treated as the document title.
     *
     * @param content The Markdown content.
     * @return The extracted title, or null if none is found.
     */
    private String markdownTitle(String content) {
        String[] lines = content.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                String heading = trimmed.replaceFirst("^#+\\s*", "").trim();
                if ("title".equalsIgnoreCase(heading)) {
                    return nextBodyLine(lines, index + 1);
                }
                return heading;
            }
        }
        return null;
    }

    private String nextBodyLine(String[] lines, int startIndex) {
        for (int index = startIndex; index < lines.length; index++) {
            String trimmed = lines[index].trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                return null;
            }
            return trimmed;
        }
        return null;
    }
}
