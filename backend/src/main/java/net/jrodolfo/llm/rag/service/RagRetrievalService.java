package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.rag.store.RagRetrievalStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for retrieving relevant documents from the RAG corpus based on a user's question.
 * It delegates ranking to the configured retrieval store.
 */
@Service
public class RagRetrievalService {

    private final RagProperties ragProperties;
    private final RagCorpusService ragCorpusService;
    private final RagRetrievalStore ragRetrievalStore;

    /**
     * Constructs a new RagRetrievalService.
     *
     * @param ragProperties    configuration properties for RAG
     * @param ragCorpusService service for managing the RAG corpus indexing
     * @param ragRetrievalStore the store used for document retrieval
     */
    public RagRetrievalService(
            RagProperties ragProperties,
            RagCorpusService ragCorpusService,
            RagRetrievalStore ragRetrievalStore
    ) {
        this.ragProperties = ragProperties;
        this.ragCorpusService = ragCorpusService;
        this.ragRetrievalStore = ragRetrievalStore;
    }

    /**
     * Retrieves a list of relevant source chunks for the given question.
     *
     * @param question the query for which relevant documents should be found
     * @return a list of {@link RagMatch} objects representing the most relevant chunks
     */
    public List<RagMatch> retrieve(String question) {
        ragCorpusService.ensureIndexed();
        return ragRetrievalStore.search(question, ragProperties.topK());
    }
}
