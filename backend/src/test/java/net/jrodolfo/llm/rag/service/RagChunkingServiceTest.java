package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.model.RagDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagChunkingServiceTest {

    private final RagChunkingService service = new RagChunkingService();

    @Test
    void chunkDocumentsSplitsLargeMarkdownIntoMultipleChunks() {
        RagDocument document = new RagDocument(
                Path.of("architecture.md"),
                "Architecture",
                """
                # Architecture

                This project keeps backend, frontend, and MCP concerns separated.

                ## Providers

                Ollama, Bedrock, and Hugging Face all sit behind the provider registry.

                ## Tooling

                MCP tools enrich prompts with structured data before the final answer is generated.
                """.repeat(3)
        );

        var chunks = service.chunkDocuments(List.of(document), 140, 30);

        assertTrue(chunks.size() >= 3);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.sourcePath().equals("architecture.md")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.text().length() <= 140));
        assertFalse(chunks.getFirst().text().isBlank());
    }
}
