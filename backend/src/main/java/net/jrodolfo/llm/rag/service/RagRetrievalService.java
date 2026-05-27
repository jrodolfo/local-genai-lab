package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.rag.store.RagVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagRetrievalService {

    private final RagProperties ragProperties;
    private final RagCorpusService ragCorpusService;
    private final RagVectorStore ragVectorStore;

    public RagRetrievalService(
            RagProperties ragProperties,
            RagCorpusService ragCorpusService,
            RagVectorStore ragVectorStore
    ) {
        this.ragProperties = ragProperties;
        this.ragCorpusService = ragCorpusService;
        this.ragVectorStore = ragVectorStore;
    }

    public List<RagMatch> retrieve(String question) {
        ragCorpusService.ensureIndexed();
        return ragVectorStore.search(question, ragProperties.topK());
    }
}
