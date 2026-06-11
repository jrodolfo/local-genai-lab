package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.dto.RagRetrievalMetadata;
import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.config.RagRetrievalMode;
import net.jrodolfo.llm.rag.config.RagRetrievalTarget;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.rag.store.RagRetrievalStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for retrieving relevant chunks from the RAG corpus.
 *
 * <p>Requests may use the backend's configured default retrieval target or pass
 * an explicit target selected in the UI. Explicit targets are intentionally
 * resolved here so the rest of the answer flow receives consistent source
 * chunks and metadata.
 */
@Service
public class RagRetrievalService {

    private final RagProperties ragProperties;
    private final RagCorpusService ragCorpusService;
    private final RagRetrievalStore ragRetrievalStore;
    private final RagVectorRetrievalService ragVectorRetrievalService;

    /**
     * Constructs a new RagRetrievalService.
     *
     * @param ragProperties    configuration properties for RAG
     * @param ragCorpusService service for managing the RAG corpus indexing
     * @param ragRetrievalStore the store used for document retrieval
     * @param ragVectorRetrievalService the vector retrieval service
     */
    public RagRetrievalService(
            RagProperties ragProperties,
            RagCorpusService ragCorpusService,
            RagRetrievalStore ragRetrievalStore,
            RagVectorRetrievalService ragVectorRetrievalService
    ) {
        this.ragProperties = ragProperties;
        this.ragCorpusService = ragCorpusService;
        this.ragRetrievalStore = ragRetrievalStore;
        this.ragVectorRetrievalService = ragVectorRetrievalService;
    }

    /**
     * Retrieves a list of relevant source chunks for the given question.
     *
     * @param question the query for which relevant documents should be found
     * @return a list of {@link RagMatch} objects representing the most relevant chunks
     */
    public List<RagMatch> retrieve(String question) {
        return retrieve(question, RagRetrievalTarget.fromRequestOrDefault(null, ragProperties));
    }

    /**
     * Retrieves source chunks with an explicit request-level retrieval target.
     *
     * @param question the user's question
     * @param target   the retrieval target to use for this request
     * @return ranked source chunks for the target
     */
    public List<RagMatch> retrieve(String question, RagRetrievalTarget target) {
        ragCorpusService.ensureIndexed(target);
        return switch (target.retrievalMode()) {
            case LEXICAL -> ragRetrievalStore.search(question, ragProperties.topK());
            case VECTOR -> ragVectorRetrievalService.retrieve(question, ragProperties.topK(), target.vectorStoreMode());
        };
    }

    /**
     * Describes the active retrieval target used by {@link #retrieve(String)}.
     *
     * @return retrieval metadata for the current RAG configuration
     */
    public RagRetrievalMetadata activeMetadata() {
        return activeMetadata(RagRetrievalTarget.fromRequestOrDefault(null, ragProperties));
    }

    /**
     * Builds metadata describing a specific retrieval target.
     *
     * @param target the retrieval target used for the current answer
     * @return metadata persisted with the RAG assistant message
     */
    public RagRetrievalMetadata activeMetadata(RagRetrievalTarget target) {
        boolean vectorMode = target.retrievalMode() == RagRetrievalMode.VECTOR;
        return new RagRetrievalMetadata(
                target.retrievalMode().configValue(),
                target.retrievalStore(),
                target.vectorStoreMode().configValue(),
                target.value(),
                ragProperties.topK(),
                vectorMode ? ragProperties.embeddingProvider() : null,
                vectorMode ? ragProperties.embeddingModel() : null
        );
    }
}
