package net.jrodolfo.llm.rag.model;

import java.nio.file.Path;

/**
 * Represents a document in the RAG system.
 *
 * @param path    The file system path to the original document.
 * @param title   The title of the document, typically the filename.
 * @param content The full text content of the document.
 */
public record RagDocument(
        Path path,
        String title,
        String content
) {
}
