package net.jrodolfo.llm.rag.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagDocumentLoaderTest {

    private final RagDocumentLoader loader = new RagDocumentLoader();

    @TempDir
    private Path tempDir;

    @Test
    void loadMarkdownDocumentsUsesFirstRealHeadingAsTitle() throws IOException {
        Files.writeString(tempDir.resolve("architecture.md"), """
                # Architecture

                System overview.
                """);

        var documents = loader.loadMarkdownDocuments(tempDir);

        assertEquals("Architecture", documents.getFirst().title());
    }

    @Test
    void loadMarkdownDocumentsUsesNextBodyLineWhenFirstHeadingIsTitleField() throws IOException {
        Files.writeString(tempDir.resolve("legacy-adr.md"), """
                # Title

                Add isolated phase-1 RAG workspace over local docs corpus

                # Status

                Accepted
                """);

        var documents = loader.loadMarkdownDocuments(tempDir);

        assertEquals("Add isolated phase-1 RAG workspace over local docs corpus", documents.getFirst().title());
    }

    @Test
    void loadMarkdownDocumentsFallsBackToRelativePathWhenNoUsefulTitleExists() throws IOException {
        Files.writeString(tempDir.resolve("notes.md"), """
                # Title

                # Status

                Accepted
                """);

        var documents = loader.loadMarkdownDocuments(tempDir);

        assertEquals("notes.md", documents.getFirst().title());
    }
}
