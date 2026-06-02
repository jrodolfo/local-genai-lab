package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.embedding.EmbeddingException;
import net.jrodolfo.llm.rag.embedding.EmbeddingService;
import net.jrodolfo.llm.rag.embedding.EmbeddingVector;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagVectorIndexResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVectorIndexingServiceTest {

    @Test
    void indexEmbedsChunksInOrderAndPreservesMetadata() {
        StubEmbeddingService embeddingService = new StubEmbeddingService(List.of(
                new EmbeddingVector("nomic-embed-text", List.of(0.1, 0.2, 0.3)),
                new EmbeddingVector("nomic-embed-text", List.of(0.4, 0.5, 0.6))
        ));
        RagVectorIndexingService service = service(embeddingService);

        RagVectorIndexResult result = service.index(List.of(
                chunk("architecture.md#0", "architecture.md", "Architecture", "Provider registry selects providers."),
                chunk("sessions.md#0", "sessions.md", "Sessions", "Sessions are stored as JSON files.")
        ));

        assertEquals("ollama", result.embeddingProvider());
        assertEquals("nomic-embed-text", result.embeddingModel());
        assertEquals(2, result.chunkCount());
        assertEquals(3, result.vectorDimension());
        assertEquals(List.of(
                "Provider registry selects providers.",
                "Sessions are stored as JSON files."
        ), embeddingService.inputs());

        assertEquals("architecture.md#0", result.chunks().get(0).chunkId());
        assertEquals("architecture.md", result.chunks().get(0).sourcePath());
        assertEquals("Architecture", result.chunks().get(0).title());
        assertEquals("Provider registry selects providers.", result.chunks().get(0).text());
        assertEquals(List.of(0.1, 0.2, 0.3), result.chunks().get(0).vector());

        assertEquals("sessions.md#0", result.chunks().get(1).chunkId());
        assertEquals("sessions.md", result.chunks().get(1).sourcePath());
        assertEquals("Sessions", result.chunks().get(1).title());
        assertEquals("Sessions are stored as JSON files.", result.chunks().get(1).text());
        assertEquals(List.of(0.4, 0.5, 0.6), result.chunks().get(1).vector());
    }

    @Test
    void indexRejectsEmptyChunkList() {
        RagVectorIndexingService service = service(new StubEmbeddingService(List.of()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.index(List.of()));

        assertEquals("Chunks to vector-index must not be empty.", exception.getMessage());
    }

    @Test
    void indexRejectsNullChunkEntries() {
        RagVectorIndexingService service = service(new StubEmbeddingService(List.of()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.index(listWithNullChunk()));

        assertEquals("Chunks to vector-index must not contain null entries.", exception.getMessage());
    }

    @Test
    void indexWrapsEmbeddingFailureWithChunkContext() {
        RagVectorIndexingService service = service(new FailingEmbeddingService());

        RagVectorIndexingException exception = assertThrows(
                RagVectorIndexingException.class,
                () -> service.index(List.of(chunk("adr.md#1", "adr.md", "ADR", "Decision context.")))
        );

        assertEquals("Failed to embed RAG chunk adr.md#1 from adr.md.", exception.getMessage());
        assertTrue(exception.getCause() instanceof EmbeddingException);
    }

    @Test
    void indexRejectsMissingEmbeddingProvider() {
        RagVectorIndexingService service = new RagVectorIndexingService(
                new StubEmbeddingService(List.of(new EmbeddingVector("nomic-embed-text", List.of(1.0)))),
                new RagProperties(true, "docs", 900, 160, 4, "lexical", " ", "nomic-embed-text")
        );

        RagVectorIndexingException exception = assertThrows(
                RagVectorIndexingException.class,
                () -> service.index(List.of(chunk("docs.md#0", "docs.md", "Docs", "Document text.")))
        );

        assertEquals("RAG embedding provider must be configured.", exception.getMessage());
    }

    private static RagVectorIndexingService service(EmbeddingService embeddingService) {
        return new RagVectorIndexingService(
                embeddingService,
                new RagProperties(true, "docs", 900, 160, 4, "lexical", "ollama", "nomic-embed-text")
        );
    }

    private static RagChunk chunk(String id, String sourcePath, String title, String text) {
        return new RagChunk(id, sourcePath, title, text);
    }

    private static List<RagChunk> listWithNullChunk() {
        List<RagChunk> chunks = new ArrayList<>();
        chunks.add(null);
        return chunks;
    }

    private static final class StubEmbeddingService implements EmbeddingService {
        private final List<EmbeddingVector> vectors;
        private final List<String> inputs = new ArrayList<>();
        private int index;

        private StubEmbeddingService(List<EmbeddingVector> vectors) {
            this.vectors = vectors;
        }

        @Override
        public EmbeddingVector embed(String text) {
            inputs.add(text);
            return vectors.get(index++);
        }

        List<String> inputs() {
            return inputs;
        }
    }

    private static final class FailingEmbeddingService implements EmbeddingService {

        @Override
        public EmbeddingVector embed(String text) {
            throw new EmbeddingException("embedding failed");
        }
    }
}
