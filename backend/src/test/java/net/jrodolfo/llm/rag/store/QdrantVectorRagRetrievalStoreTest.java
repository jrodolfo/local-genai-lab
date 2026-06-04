package net.jrodolfo.llm.rag.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.qdrant.QdrantClient;
import net.jrodolfo.llm.rag.qdrant.QdrantClientException;
import net.jrodolfo.llm.rag.qdrant.QdrantPointPayload;
import net.jrodolfo.llm.rag.qdrant.QdrantSearchResult;
import net.jrodolfo.llm.rag.service.RagVectorRetrievalException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QdrantVectorRagRetrievalStoreTest {

    @Test
    void mapsQdrantResultsToRagMatches() {
        RecordingQdrantClient qdrantClient = new RecordingQdrantClient(List.of(new QdrantSearchResult(
                "architecture.md#0",
                0.87,
                new QdrantPointPayload(
                        "architecture.md",
                        "architecture.md#0",
                        "Architecture",
                        "Provider registry text.",
                        "docs",
                        "ollama",
                        "nomic-embed-text",
                        "2026-06-04T00:00:00Z",
                        "abc123"
                )
        )));
        QdrantVectorRagRetrievalStore store = new QdrantVectorRagRetrievalStore(properties(), qdrantClient);

        var matches = store.searchByVector(List.of(0.1, 0.2), 4);

        assertEquals("http://localhost:6333", qdrantClient.qdrantUrl);
        assertEquals("local_genai_lab_docs", qdrantClient.collectionName);
        assertEquals(List.of(0.1, 0.2), qdrantClient.queryVector);
        assertEquals(4, qdrantClient.topK);
        assertEquals(1, matches.size());
        assertEquals("architecture.md#0", matches.getFirst().chunk().id());
        assertEquals("architecture.md", matches.getFirst().chunk().sourcePath());
        assertEquals("Architecture", matches.getFirst().chunk().title());
        assertEquals("Provider registry text.", matches.getFirst().chunk().text());
        assertEquals(0.87, matches.getFirst().score());
    }

    @Test
    void emptyQdrantResultsFailClearly() {
        QdrantVectorRagRetrievalStore store = new QdrantVectorRagRetrievalStore(
                properties(),
                new RecordingQdrantClient(List.of())
        );

        RagVectorRetrievalException ex = assertThrows(
                RagVectorRetrievalException.class,
                () -> store.searchByVector(List.of(0.1, 0.2), 4)
        );

        assertEquals(
                "Qdrant vector retrieval is selected, but no Qdrant index is available yet. Use RAG_VECTOR_STORE=in-memory or add Qdrant indexing before querying.",
                ex.getMessage()
        );
    }

    @Test
    void qdrantClientFailureFailsClearly() {
        QdrantVectorRagRetrievalStore store = new QdrantVectorRagRetrievalStore(
                properties(),
                new FailingQdrantClient()
        );

        RagVectorRetrievalException ex = assertThrows(
                RagVectorRetrievalException.class,
                () -> store.searchByVector(List.of(0.1, 0.2), 4)
        );

        assertEquals(
                "Qdrant vector retrieval is selected, but no Qdrant index is available yet. Use RAG_VECTOR_STORE=in-memory or add Qdrant indexing before querying.",
                ex.getMessage()
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
        private final List<QdrantSearchResult> results;
        private String qdrantUrl;
        private String collectionName;
        private List<Double> queryVector;
        private int topK;

        private RecordingQdrantClient(List<QdrantSearchResult> results) {
            super(new ObjectMapper());
            this.results = results;
        }

        @Override
        public List<QdrantSearchResult> search(String qdrantUrl, String collectionName, List<Double> queryVector, int topK) {
            this.qdrantUrl = qdrantUrl;
            this.collectionName = collectionName;
            this.queryVector = queryVector;
            this.topK = topK;
            return results;
        }
    }

    private static final class FailingQdrantClient extends QdrantClient {
        private FailingQdrantClient() {
            super(new ObjectMapper());
        }

        @Override
        public List<QdrantSearchResult> search(String qdrantUrl, String collectionName, List<Double> queryVector, int topK) {
            throw new QdrantClientException("Qdrant failed.");
        }
    }
}
