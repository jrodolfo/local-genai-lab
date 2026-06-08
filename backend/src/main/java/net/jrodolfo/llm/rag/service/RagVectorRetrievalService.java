package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.config.RagRetrievalTarget;
import net.jrodolfo.llm.rag.config.RagVectorStoreMode;
import net.jrodolfo.llm.rag.embedding.EmbeddingService;
import net.jrodolfo.llm.rag.embedding.EmbeddingVector;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.rag.store.InMemoryVectorRagRetrievalStore;
import net.jrodolfo.llm.rag.store.QdrantVectorRagRetrievalStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Query-time vector retrieval service for future RAG vector mode.
 *
 * <p>The service embeds the user question, then delegates vector math and
 * ranking to the in-memory vector store. It is not wired into the active
 * lexical RAG query flow yet.
 */
@Service
public class RagVectorRetrievalService {

    private final RagProperties ragProperties;
    private final EmbeddingService embeddingService;
    private final InMemoryVectorRagRetrievalStore inMemoryVectorStore;
    private final QdrantVectorRagRetrievalStore qdrantVectorStore;

    @Autowired
    public RagVectorRetrievalService(
            RagProperties ragProperties,
            EmbeddingService embeddingService,
            InMemoryVectorRagRetrievalStore inMemoryVectorStore,
            QdrantVectorRagRetrievalStore qdrantVectorStore
    ) {
        this.ragProperties = ragProperties;
        this.embeddingService = embeddingService;
        this.inMemoryVectorStore = inMemoryVectorStore;
        this.qdrantVectorStore = qdrantVectorStore;
    }

    public RagVectorRetrievalService(
            EmbeddingService embeddingService,
            InMemoryVectorRagRetrievalStore vectorStore
    ) {
        this(
                new RagProperties(true, "docs", 900, 160, 4, "vector", "ollama", "nomic-embed-text"),
                embeddingService,
                vectorStore,
                null
        );
    }

    /**
     * Searches the vector store using an embedding of the question.
     *
     * @param question user question
     * @param topK     maximum number of matches
     * @return vector-ranked matches
     */
    public List<RagMatch> retrieve(String question, int topK) {
        RagRetrievalTarget target = RagRetrievalTarget.fromRequestOrDefault(null, ragProperties);
        return retrieve(question, topK, target.vectorStoreMode());
    }

    public List<RagMatch> retrieve(String question, int topK, RagVectorStoreMode vectorStoreMode) {
        if (question == null || question.isBlank() || topK <= 0) {
            return List.of();
        }
        try {
            EmbeddingVector queryEmbedding = embeddingService.embed(question);
            return switch (vectorStoreMode) {
                case IN_MEMORY -> inMemoryVectorStore.searchByVector(queryEmbedding.values(), topK);
                case QDRANT -> {
                    if (qdrantVectorStore == null) {
                        throw new RagVectorRetrievalException("Qdrant vector retrieval store is not configured.");
                    }
                    yield qdrantVectorStore.searchByVector(queryEmbedding.values(), topK);
                }
            };
        } catch (RuntimeException ex) {
            if (ex instanceof RagVectorRetrievalException ragVectorRetrievalException) {
                throw ragVectorRetrievalException;
            }
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new RagVectorRetrievalException("Failed to retrieve RAG chunks with vector search.", ex);
        }
    }
}
