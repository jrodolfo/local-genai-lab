package net.jrodolfo.llm.rag.store;

import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagMatch;

import java.util.List;

public interface RagVectorStore {

    void replaceAll(List<RagChunk> chunks);

    List<RagMatch> search(String query, int topK);
}
