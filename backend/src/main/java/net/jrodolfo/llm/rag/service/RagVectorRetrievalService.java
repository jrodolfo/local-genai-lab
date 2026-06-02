package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.embedding.EmbeddingService;
import net.jrodolfo.llm.rag.embedding.EmbeddingVector;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.rag.store.InMemoryVectorRagRetrievalStore;
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

    private final EmbeddingService embeddingService;
    private final InMemoryVectorRagRetrievalStore vectorStore;

    public RagVectorRetrievalService(
            EmbeddingService embeddingService,
            InMemoryVectorRagRetrievalStore vectorStore
    ) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * Searches the vector store using an embedding of the question.
     *
     * @param question user question
     * @param topK     maximum number of matches
     * @return vector-ranked matches
     */
    public List<RagMatch> retrieve(String question, int topK) {
        if (question == null || question.isBlank() || topK <= 0) {
            return List.of();
        }
        try {
            EmbeddingVector queryEmbedding = embeddingService.embed(question);
            return vectorStore.searchByVector(queryEmbedding.values(), topK);
        } catch (RuntimeException ex) {
            throw new RagVectorRetrievalException("Failed to retrieve RAG chunks with vector search.", ex);
        }
    }
}
