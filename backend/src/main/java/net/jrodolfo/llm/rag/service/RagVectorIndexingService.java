package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.embedding.EmbeddingService;
import net.jrodolfo.llm.rag.embedding.EmbeddingVector;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagEmbeddedChunk;
import net.jrodolfo.llm.rag.model.RagVectorIndexResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds embedded chunk records for active RAG vector retrieval stores.
 *
 * <p>The generated embedded chunks can populate the in-memory vector index or
 * the configured Qdrant collection during a rebuild.
 */
@Service
public class RagVectorIndexingService {

    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;

    public RagVectorIndexingService(EmbeddingService embeddingService, RagProperties ragProperties) {
        this.embeddingService = embeddingService;
        this.ragProperties = ragProperties;
    }

    /**
     * Embeds the provided chunks and returns an in-process vector index result.
     *
     * @param chunks chunks to embed
     * @return vector index result
     */
    public RagVectorIndexResult index(List<RagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("Chunks to vector-index must not be empty.");
        }

        String embeddingProvider = resolveEmbeddingProvider();
        List<RagEmbeddedChunk> embeddedChunks = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            if (chunk == null) {
                throw new IllegalArgumentException("Chunks to vector-index must not contain null entries.");
            }
            embeddedChunks.add(embedChunk(chunk));
        }

        RagEmbeddedChunk firstChunk = embeddedChunks.getFirst();
        return new RagVectorIndexResult(
                embeddingProvider,
                firstChunk.embeddingModel(),
                embeddedChunks.size(),
                firstChunk.vectorDimension(),
                embeddedChunks
        );
    }

    private RagEmbeddedChunk embedChunk(RagChunk chunk) {
        try {
            EmbeddingVector embedding = embeddingService.embed(chunk.text());
            return new RagEmbeddedChunk(
                    chunk.id(),
                    chunk.sourcePath(),
                    chunk.title(),
                    chunk.text(),
                    embedding.model(),
                    embedding.values().size(),
                    embedding.values()
            );
        } catch (RuntimeException ex) {
            throw new RagVectorIndexingException("Failed to embed RAG chunk "
                    + chunk.id() + " from " + chunk.sourcePath() + ".", ex);
        }
    }

    private String resolveEmbeddingProvider() {
        String provider = ragProperties.embeddingProvider();
        if (provider == null || provider.isBlank()) {
            throw new RagVectorIndexingException("RAG embedding provider must be configured.");
        }
        return provider.trim();
    }
}
