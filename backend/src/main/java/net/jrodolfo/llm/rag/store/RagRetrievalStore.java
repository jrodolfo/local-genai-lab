package net.jrodolfo.llm.rag.store;

import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagMatch;

import java.util.List;

/**
 * Store abstraction for indexing RAG chunks and retrieving matches for a query.
 * Implementations may use lexical ranking, embeddings, or an external vector database.
 */
public interface RagRetrievalStore {

    /**
     * Replaces all existing chunks in the store with the provided list.
     *
     * @param chunks The new list of {@link RagChunk}s to index.
     */
    void replaceAll(List<RagChunk> chunks);

    /**
     * Searches the store for chunks that are most relevant to the given query.
     *
     * @param query The search query.
     * @param topK  The maximum number of matches to return.
     * @return A list of {@link RagMatch}es, typically ordered by relevance score.
     */
    List<RagMatch> search(String query, int topK);
}
