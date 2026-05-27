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

@Service
public class RagDocumentLoader {

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

    private RagDocument toDocument(Path corpusRoot, Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String title = firstMarkdownHeading(content);
            String relativePath = corpusRoot.relativize(file).toString().replace('\\', '/');
            return new RagDocument(Path.of(relativePath), title != null ? title : relativePath, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read RAG document " + file, ex);
        }
    }

    private String firstMarkdownHeading(String content) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+\\s*", "").trim();
            }
        }
        return null;
    }
}
