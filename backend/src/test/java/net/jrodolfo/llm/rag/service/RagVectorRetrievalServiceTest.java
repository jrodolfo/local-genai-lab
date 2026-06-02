package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.embedding.EmbeddingException;
import net.jrodolfo.llm.rag.embedding.EmbeddingService;
import net.jrodolfo.llm.rag.embedding.EmbeddingVector;
import net.jrodolfo.llm.rag.model.RagEmbeddedChunk;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.rag.store.InMemoryVectorRagRetrievalStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVectorRetrievalServiceTest {

    @Test
    void retrieveEmbedsQuestionAndReturnsVectorMatches() {
        RecordingEmbeddingService embeddingService = new RecordingEmbeddingService(
                new EmbeddingVector("nomic-embed-text", List.of(1.0, 0.0))
        );
        InMemoryVectorRagRetrievalStore store = new InMemoryVectorRagRetrievalStore();
        store.replaceAllEmbedded(List.of(
                embeddedChunk("architecture.md#0", "architecture.md", "Architecture", "Provider registry docs.", List.of(1.0, 0.0)),
                embeddedChunk("sessions.md#0", "sessions.md", "Sessions", "Session persistence docs.", List.of(0.0, 1.0))
        ));
        RagVectorRetrievalService service = new RagVectorRetrievalService(embeddingService, store);

        List<RagMatch> matches = service.retrieve("How does provider selection work?", 2);

        assertEquals(List.of("How does provider selection work?"), embeddingService.inputs());
        assertEquals(1, matches.size());
        assertEquals("architecture.md#0", matches.getFirst().chunk().id());
    }

    @Test
    void retrieveReturnsEmptyForBlankQuestionWithoutEmbedding() {
        RecordingEmbeddingService embeddingService = new RecordingEmbeddingService(
                new EmbeddingVector("nomic-embed-text", List.of(1.0, 0.0))
        );
        RagVectorRetrievalService service = new RagVectorRetrievalService(
                embeddingService,
                new InMemoryVectorRagRetrievalStore()
        );

        List<RagMatch> matches = service.retrieve("   ", 2);

        assertEquals(0, matches.size());
        assertEquals(0, embeddingService.inputs().size());
    }

    @Test
    void retrieveReturnsEmptyForNonPositiveTopKWithoutEmbedding() {
        RecordingEmbeddingService embeddingService = new RecordingEmbeddingService(
                new EmbeddingVector("nomic-embed-text", List.of(1.0, 0.0))
        );
        RagVectorRetrievalService service = new RagVectorRetrievalService(
                embeddingService,
                new InMemoryVectorRagRetrievalStore()
        );

        List<RagMatch> matches = service.retrieve("question", 0);

        assertEquals(0, matches.size());
        assertEquals(0, embeddingService.inputs().size());
    }

    @Test
    void retrieveWrapsEmbeddingFailuresClearly() {
        RagVectorRetrievalService service = new RagVectorRetrievalService(
                new FailingEmbeddingService(),
                new InMemoryVectorRagRetrievalStore()
        );

        RagVectorRetrievalException exception = assertThrows(
                RagVectorRetrievalException.class,
                () -> service.retrieve("question", 2)
        );

        assertEquals("Failed to retrieve RAG chunks with vector search.", exception.getMessage());
        assertTrue(exception.getCause() instanceof EmbeddingException);
    }

    private static RagEmbeddedChunk embeddedChunk(
            String id,
            String sourcePath,
            String title,
            String text,
            List<Double> vector
    ) {
        return new RagEmbeddedChunk(id, sourcePath, title, text, "nomic-embed-text", vector.size(), vector);
    }

    private static final class RecordingEmbeddingService implements EmbeddingService {
        private final EmbeddingVector vector;
        private final java.util.ArrayList<String> inputs = new java.util.ArrayList<>();

        private RecordingEmbeddingService(EmbeddingVector vector) {
            this.vector = vector;
        }

        @Override
        public EmbeddingVector embed(String text) {
            inputs.add(text);
            return vector;
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
