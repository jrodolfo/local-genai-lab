package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.rag.store.RagVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for retrieving relevant documents from the RAG corpus based on a user's question.
 * It uses a vector store to perform similarity search.
 */
@Service
public class RagRetrievalService {

    private final RagProperties ragProperties;
    private final RagCorpusService ragCorpusService;
    private final RagVectorStore ragVectorStore;

    /**
     * Constructs a new RagRetrievalService.
     *
     * @param ragProperties    configuration properties for RAG
     * @param ragCorpusService service for managing the RAG corpus indexing
     * @param ragVectorStore   the vector store used for document retrieval
     */
    public RagRetrievalService(
            RagProperties ragProperties,
            RagCorpusService ragCorpusService,
            RagVectorStore ragVectorStore
    ) {
        this.ragProperties = ragProperties;
        this.ragCorpusService = ragCorpusService;
        this.ragVectorStore = ragVectorStore;
    }

    /**
     * Retrieves a list of relevant source chunks for the given question.
     *
     * @param question the query for which relevant documents should be found
     * @return a list of {@link RagMatch} objects representing the most relevant chunks
     */
    public List<RagMatch> retrieve(String question) {
        ragCorpusService.ensureIndexed();
        return ragVectorStore.search(question, ragProperties.topK());
    }
}
