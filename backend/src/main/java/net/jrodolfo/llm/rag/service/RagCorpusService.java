package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagDocument;
import net.jrodolfo.llm.rag.store.RagVectorStore;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrator service for the RAG corpus.
 * It manages the lifecycle of the document index, including loading, chunking, and indexing into the vector store.
 */
@Service
public class RagCorpusService {

    private final RagProperties ragProperties;
    private final RagDocumentLoader documentLoader;
    private final RagChunkingService chunkingService;
    private final RagVectorStore vectorStore;
    private final AtomicReference<CorpusSnapshot> snapshotRef = new AtomicReference<>();

    /**
     * Constructs a new RagCorpusService.
     *
     * @param ragProperties configuration properties for RAG
     * @param documentLoader service for loading documents
     * @param chunkingService service for splitting documents into chunks
     * @param vectorStore the vector store for indexing chunks
     */
    public RagCorpusService(
            RagProperties ragProperties,
            RagDocumentLoader documentLoader,
            RagChunkingService chunkingService,
            RagVectorStore vectorStore
    ) {
        this.ragProperties = ragProperties;
        this.documentLoader = documentLoader;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
    }

    /**
     * Ensures that the corpus is indexed. If it's already indexed, returns the current snapshot.
     * Otherwise, it builds the index.
     *
     * @return the current {@link CorpusSnapshot}
     */
    public synchronized CorpusSnapshot ensureIndexed() {
        CorpusSnapshot snapshot = snapshotRef.get();
        if (snapshot != null) {
            return snapshot;
        }
        return rebuildIndex();
    }

    /**
     * Rebuilds the corpus index by reloading documents, re-chunking them, and updating the vector store.
     *
     * @return the new {@link CorpusSnapshot}
     */
    public synchronized CorpusSnapshot rebuildIndex() {
        Path corpusRoot = ragProperties.resolvedCorpusRoot();
        List<RagDocument> documents = documentLoader.loadMarkdownDocuments(corpusRoot);
        List<RagChunk> chunks = chunkingService.chunkDocuments(
                documents,
                ragProperties.maxChunkSize(),
                ragProperties.chunkOverlap()
        );
        vectorStore.replaceAll(chunks);
        CorpusSnapshot snapshot = new CorpusSnapshot(corpusRoot, documents, chunks);
        snapshotRef.set(snapshot);
        return snapshot;
    }

    /**
     * Returns the current snapshot of the corpus, or null if it hasn't been indexed yet.
     *
     * @return the current {@link CorpusSnapshot} or null
     */
    public CorpusSnapshot snapshot() {
        return snapshotRef.get();
    }

    /**
     * A snapshot of the indexed corpus.
     *
     * @param corpusRoot the root directory of the corpus
     * @param documents the list of loaded documents
     * @param chunks the list of generated chunks
     */
    public record CorpusSnapshot(
            Path corpusRoot,
            List<RagDocument> documents,
            List<RagChunk> chunks
    ) {
    }
}
