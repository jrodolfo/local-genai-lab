package net.jrodolfo.llm.rag.qdrant;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.config.RagRetrievalMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Performs lightweight readiness checks for the optional Qdrant vector store.
 */
@Service
public class QdrantStatusService {

    private final QdrantClient qdrantClient;

    @Autowired
    public QdrantStatusService(QdrantClient qdrantClient) {
        this.qdrantClient = qdrantClient;
    }

    /**
     * Test convenience constructor for standalone controller tests.
     */
    public QdrantStatusService() {
        this(new QdrantClient(new ObjectMapper()));
    }

    public QdrantStatus status(RagProperties ragProperties) {
        if (!isQdrantRequired(ragProperties)) {
            return QdrantStatus.notRequired();
        }

        try {
            QdrantCollectionInfo collectionInfo = qdrantClient.collectionInfo(
                    ragProperties.qdrantUrl(),
                    ragProperties.qdrantCollection()
            );
            if (collectionInfo.exists()) {
                return QdrantStatus.collectionPresent(ragProperties.qdrantCollection(), collectionInfo.pointCount());
            }
            return QdrantStatus.collectionMissing(ragProperties.qdrantCollection());
        } catch (QdrantClientException | IllegalArgumentException ex) {
            return QdrantStatus.unavailable(ragProperties.qdrantUrl());
        }
    }

    private boolean isQdrantRequired(RagProperties ragProperties) {
        if (!ragProperties.enabled()) {
            return false;
        }
        RagRetrievalMode mode = RagRetrievalMode.fromConfig(ragProperties.retrievalMode());
        return mode == RagRetrievalMode.VECTOR
                && "qdrant".equals(ragProperties.vectorStore().toLowerCase(Locale.ROOT).trim());
    }
}
