package net.jrodolfo.llm.rag.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagEmbeddedChunk;
import net.jrodolfo.llm.rag.model.RagVectorIndexResult;
import net.jrodolfo.llm.rag.qdrant.QdrantClient;
import net.jrodolfo.llm.rag.qdrant.QdrantClientException;
import net.jrodolfo.llm.rag.qdrant.QdrantPoint;
import net.jrodolfo.llm.rag.service.RagVectorIndexingException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantVectorRagIndexingStoreTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-04T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void recreatesCollectionAndUpsertsEmbeddedChunks() {
        RecordingQdrantClient qdrantClient = new RecordingQdrantClient();
        QdrantVectorRagIndexingStore store = new QdrantVectorRagIndexingStore(properties(), qdrantClient, FIXED_CLOCK);

        store.replaceAllEmbedded(indexResult(), Path.of("/repo/docs"));

        assertEquals("http://localhost:6333", qdrantClient.recreatedUrl);
        assertEquals("local_genai_lab_docs", qdrantClient.recreatedCollection);
        assertEquals(3, qdrantClient.recreatedVectorSize);
        assertEquals("http://localhost:6333", qdrantClient.upsertedUrl);
        assertEquals("local_genai_lab_docs", qdrantClient.upsertedCollection);
        assertEquals(2, qdrantClient.upsertedPoints.size());

        QdrantPoint firstPoint = qdrantClient.upsertedPoints.getFirst();
        assertNotEquals("architecture.md#0", firstPoint.id());
        assertTrue(firstPoint.id().matches("[0-9a-f\\-]{36}"));
        assertEquals(List.of(0.1, 0.2, 0.3), firstPoint.vector());
        assertEquals("architecture.md", firstPoint.payload().sourcePath());
        assertEquals("architecture.md#0", firstPoint.payload().chunkId());
        assertEquals("Architecture", firstPoint.payload().title());
        assertEquals("Provider registry selects providers.", firstPoint.payload().text());
        assertEquals("/repo/docs", firstPoint.payload().corpusRoot());
        assertEquals("ollama", firstPoint.payload().embeddingProvider());
        assertEquals("nomic-embed-text", firstPoint.payload().embeddingModel());
        assertEquals("2026-06-04T12:00:00Z", firstPoint.payload().indexedAt());
        assertTrue(firstPoint.payload().contentHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void qdrantFailureFailsClearly() {
        QdrantVectorRagIndexingStore store = new QdrantVectorRagIndexingStore(
                properties(),
                new FailingQdrantClient(),
                FIXED_CLOCK
        );

        RagVectorIndexingException exception = assertThrows(
                RagVectorIndexingException.class,
                () -> store.replaceAllEmbedded(indexResult(), Path.of("/repo/docs"))
        );

        assertEquals(
                "Failed to index RAG chunks in Qdrant at http://localhost:6333. "
                        + "Confirm Qdrant is running and reachable, then click Rebuild Index or run Compare Retrieval Targets again. "
                        + "For the local Docker setup, run: docker compose up -d qdrant. "
                        + "Details: Qdrant failed.",
                exception.getMessage()
        );
        assertTrue(exception.getCause() instanceof QdrantClientException);
    }

    private static RagVectorIndexResult indexResult() {
        return new RagVectorIndexResult(
                "ollama",
                "nomic-embed-text",
                2,
                3,
                List.of(
                        new RagEmbeddedChunk(
                                "architecture.md#0",
                                "architecture.md",
                                "Architecture",
                                "Provider registry selects providers.",
                                "nomic-embed-text",
                                3,
                                List.of(0.1, 0.2, 0.3)
                        ),
                        new RagEmbeddedChunk(
                                "sessions.md#0",
                                "sessions.md",
                                "Sessions",
                                "Sessions are persisted as local JSON files.",
                                "nomic-embed-text",
                                3,
                                List.of(0.4, 0.5, 0.6)
                        )
                )
        );
    }

    private static RagProperties properties() {
        return new RagProperties(
                true,
                "docs",
                900,
                160,
                4,
                "vector",
                "qdrant",
                "http://localhost:6333",
                "local_genai_lab_docs",
                "ollama",
                "nomic-embed-text"
        );
    }

    private static class RecordingQdrantClient extends QdrantClient {
        private String recreatedUrl;
        private String recreatedCollection;
        private int recreatedVectorSize;
        private String upsertedUrl;
        private String upsertedCollection;
        private List<QdrantPoint> upsertedPoints = List.of();

        private RecordingQdrantClient() {
            super(new ObjectMapper());
        }

        @Override
        public void recreateCollection(String qdrantUrl, String collectionName, int vectorSize) {
            this.recreatedUrl = qdrantUrl;
            this.recreatedCollection = collectionName;
            this.recreatedVectorSize = vectorSize;
        }

        @Override
        public void upsertPoints(String qdrantUrl, String collectionName, List<QdrantPoint> points) {
            this.upsertedUrl = qdrantUrl;
            this.upsertedCollection = collectionName;
            this.upsertedPoints = points;
        }
    }

    private static final class FailingQdrantClient extends QdrantClient {
        private FailingQdrantClient() {
            super(new ObjectMapper());
        }

        @Override
        public void recreateCollection(String qdrantUrl, String collectionName, int vectorSize) {
            throw new QdrantClientException("Qdrant failed.");
        }
    }
}
