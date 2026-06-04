package net.jrodolfo.llm.rag.store;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.rag.qdrant.QdrantClient;
import net.jrodolfo.llm.rag.qdrant.QdrantClientException;
import net.jrodolfo.llm.rag.qdrant.QdrantPointPayload;
import net.jrodolfo.llm.rag.qdrant.QdrantSearchResult;
import net.jrodolfo.llm.rag.service.RagVectorRetrievalException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Qdrant-backed vector retrieval store for phase-2 RAG experiments.
 */
@Component
public class QdrantVectorRagRetrievalStore {

    private static final String NO_INDEX_MESSAGE = "Qdrant vector retrieval is selected, but no Qdrant index is available yet. "
            + "Use RAG_VECTOR_STORE=in-memory or add Qdrant indexing before querying.";

    private final RagProperties ragProperties;
    private final QdrantClient qdrantClient;

    public QdrantVectorRagRetrievalStore(RagProperties ragProperties, QdrantClient qdrantClient) {
        this.ragProperties = ragProperties;
        this.qdrantClient = qdrantClient;
    }

    /**
     * Searches Qdrant by query vector.
     *
     * @param queryVector query embedding vector
     * @param topK        maximum number of matches
     * @return relevant matches mapped back to RAG chunks
     */
    public List<RagMatch> searchByVector(List<Double> queryVector, int topK) {
        if (queryVector == null || queryVector.isEmpty() || topK <= 0) {
            return List.of();
        }
        try {
            List<QdrantSearchResult> results = qdrantClient.search(
                    ragProperties.qdrantUrl(),
                    ragProperties.qdrantCollection(),
                    queryVector,
                    topK
            );
            if (results.isEmpty()) {
                throw new RagVectorRetrievalException(NO_INDEX_MESSAGE);
            }
            return results.stream()
                    .map(this::toRagMatch)
                    .toList();
        } catch (QdrantClientException ex) {
            throw new RagVectorRetrievalException(NO_INDEX_MESSAGE, ex);
        }
    }

    private RagMatch toRagMatch(QdrantSearchResult result) {
        QdrantPointPayload payload = result.payload();
        return new RagMatch(
                new RagChunk(
                        payload.chunkId(),
                        payload.sourcePath(),
                        payload.title(),
                        payload.text()
                ),
                result.score()
        );
    }
}
