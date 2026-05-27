package net.jrodolfo.llm.rag.model;

import java.nio.file.Path;

public record RagDocument(
        Path path,
        String title,
        String content
) {
}
