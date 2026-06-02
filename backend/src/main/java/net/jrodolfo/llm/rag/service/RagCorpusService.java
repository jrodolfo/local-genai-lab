package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.config.RagRetrievalMode;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagDocument;
import net.jrodolfo.llm.rag.model.RagVectorIndexResult;
import net.jrodolfo.llm.rag.store.RagRetrievalStore;
import net.jrodolfo.llm.rag.store.InMemoryVectorRagRetrievalStore;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrator service for the RAG corpus.
 * It manages the lifecycle of the document index, including loading, chunking, and indexing into the retrieval store.
 */
@Service
public class RagCorpusService {

    private final RagProperties ragProperties;
    private final RagDocumentLoader documentLoader;
    private final RagChunkingService chunkingService;
    private final RagRetrievalStore retrievalStore;
    private final RagVectorIndexingService vectorIndexingService;
    private final InMemoryVectorRagRetrievalStore vectorRetrievalStore;
    private final AtomicReference<CorpusSnapshot> snapshotRef = new AtomicReference<>();

    /**
     * Constructs a new RagCorpusService.
     *
     * @param ragProperties   configuration properties for RAG
     * @param documentLoader  service for loading documents
     * @param chunkingService service for splitting documents into chunks
     * @param retrievalStore  the retrieval store for indexing chunks
     * @param vectorIndexingService service for embedding chunks in vector mode
     * @param vectorRetrievalStore vector retrieval store used in vector mode
     */
    public RagCorpusService(
            RagProperties ragProperties,
            RagDocumentLoader documentLoader,
            RagChunkingService chunkingService,
            RagRetrievalStore retrievalStore,
            RagVectorIndexingService vectorIndexingService,
            InMemoryVectorRagRetrievalStore vectorRetrievalStore
    ) {
        this.ragProperties = ragProperties;
        this.documentLoader = documentLoader;
        this.chunkingService = chunkingService;
        this.retrievalStore = retrievalStore;
        this.vectorIndexingService = vectorIndexingService;
        this.vectorRetrievalStore = vectorRetrievalStore;
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
     * Rebuilds the corpus index by reloading documents, re-chunking them, and updating the retrieval store.
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
        RagRetrievalMode mode = RagRetrievalMode.fromConfig(ragProperties.retrievalMode());
        if (mode == RagRetrievalMode.VECTOR) {
            RagVectorIndexResult vectorIndex = vectorIndexingService.index(chunks);
            vectorRetrievalStore.replaceAllEmbedded(vectorIndex.chunks());
        } else {
            retrievalStore.replaceAll(chunks);
        }
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
     * @param documents  the list of loaded documents
     * @param chunks     the list of generated chunks
     */
    public record CorpusSnapshot(
            Path corpusRoot,
            List<RagDocument> documents,
            List<RagChunk> chunks
    ) {
    }
}
