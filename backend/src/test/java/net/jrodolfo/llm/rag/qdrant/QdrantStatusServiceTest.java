package net.jrodolfo.llm.rag.qdrant;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.rag.config.RagProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantStatusServiceTest {

    @Test
    void reportsNotRequiredForLexicalMode() {
        QdrantStatus status = new QdrantStatusService(new RecordingQdrantClient(QdrantCollectionInfo.missing()))
                .status(properties("lexical", "qdrant"));

        assertFalse(status.required());
        assertNull(status.reachable());
        assertNull(status.collectionName());
        assertNull(status.collectionExists());
        assertNull(status.pointCount());
        assertEquals("Qdrant is not required for the current RAG configuration.", status.message());
    }

    @Test
    void reportsCollectionPresentWithPointCount() {
        QdrantStatus status = new QdrantStatusService(new RecordingQdrantClient(new QdrantCollectionInfo(true, 123L)))
                .status(properties("vector", "qdrant"));

        assertTrue(status.required());
        assertTrue(status.reachable());
        assertEquals("local_genai_lab_docs", status.collectionName());
        assertTrue(status.collectionExists());
        assertEquals(123L, status.pointCount());
        assertEquals("Qdrant collection local_genai_lab_docs is present with 123 points.", status.message());
    }

    @Test
    void reportsCollectionPresentWithUnknownPointCount() {
        QdrantStatus status = new QdrantStatusService(new RecordingQdrantClient(new QdrantCollectionInfo(true, null)))
                .status(properties("vector", "qdrant"));

        assertTrue(status.required());
        assertTrue(status.reachable());
        assertEquals("local_genai_lab_docs", status.collectionName());
        assertTrue(status.collectionExists());
        assertNull(status.pointCount());
        assertEquals("Qdrant collection local_genai_lab_docs is present with an unknown number of points.", status.message());
    }

    @Test
    void reportsCollectionMissing() {
        QdrantStatus status = new QdrantStatusService(new RecordingQdrantClient(QdrantCollectionInfo.missing()))
                .status(properties("vector", "qdrant"));

        assertTrue(status.required());
        assertTrue(status.reachable());
        assertEquals("local_genai_lab_docs", status.collectionName());
        assertFalse(status.collectionExists());
        assertNull(status.pointCount());
        assertEquals("Qdrant collection local_genai_lab_docs is missing. Rebuild the index.", status.message());
    }

    @Test
    void reportsUnavailableWhenQdrantRequestFails() {
        QdrantStatus status = new QdrantStatusService(new FailingQdrantClient())
                .status(properties("vector", "qdrant"));

        assertTrue(status.required());
        assertFalse(status.reachable());
        assertNull(status.collectionName());
        assertNull(status.collectionExists());
        assertNull(status.pointCount());
        assertEquals("Qdrant is not reachable at http://localhost:6333.", status.message());
    }

    @Test
    void reportsNotRequiredForInMemoryVectorMode() {
        QdrantStatus status = new QdrantStatusService(new RecordingQdrantClient(QdrantCollectionInfo.missing()))
                .status(properties("vector", "in-memory"));

        assertFalse(status.required());
        assertNull(status.reachable());
        assertEquals("Qdrant is not required for the current RAG configuration.", status.message());
    }

    private static RagProperties properties(String retrievalMode, String vectorStore) {
        return new RagProperties(
                true,
                "docs",
                900,
                160,
                4,
                retrievalMode,
                vectorStore,
                "http://localhost:6333",
                "local_genai_lab_docs",
                "ollama",
                "nomic-embed-text"
        );
    }

    private static class RecordingQdrantClient extends QdrantClient {
        private final QdrantCollectionInfo info;

        private RecordingQdrantClient(QdrantCollectionInfo info) {
            super(new ObjectMapper());
            this.info = info;
        }

        @Override
        public QdrantCollectionInfo collectionInfo(String qdrantUrl, String collectionName) {
            return info;
        }
    }

    private static final class FailingQdrantClient extends QdrantClient {
        private FailingQdrantClient() {
            super(new ObjectMapper());
        }

        @Override
        public QdrantCollectionInfo collectionInfo(String qdrantUrl, String collectionName) {
            throw new QdrantClientException("connection refused");
        }
    }
}
