package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.config.RagRetrievalMode;
import net.jrodolfo.llm.rag.config.RagRetrievalTarget;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagDocument;
import net.jrodolfo.llm.rag.model.RagVectorIndexResult;
import net.jrodolfo.llm.rag.store.RagRetrievalStore;
import net.jrodolfo.llm.rag.store.InMemoryVectorRagRetrievalStore;
import net.jrodolfo.llm.rag.store.QdrantVectorRagIndexingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Orchestrates loading, chunking, and indexing the fixed local RAG corpus.
 *
 * <p>The service keeps one corpus snapshot but tracks which retrieval target
 * has been indexed. This supports lazy indexing for the selected target while
 * keeping lexical and vector stores isolated from each other.
 */
@Service
public class RagCorpusService {

    private final RagProperties ragProperties;
    private final RagDocumentLoader documentLoader;
    private final RagChunkingService chunkingService;
    private final RagRetrievalStore retrievalStore;
    private final RagVectorIndexingService vectorIndexingService;
    private final InMemoryVectorRagRetrievalStore vectorRetrievalStore;
    private final QdrantVectorRagIndexingStore qdrantVectorIndexingStore;
    private final AtomicReference<CorpusSnapshot> snapshotRef = new AtomicReference<>();
    private final Set<RagRetrievalTarget> indexedTargets = EnumSet.noneOf(RagRetrievalTarget.class);

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
    @Autowired
    public RagCorpusService(
            RagProperties ragProperties,
            RagDocumentLoader documentLoader,
            RagChunkingService chunkingService,
            RagRetrievalStore retrievalStore,
            RagVectorIndexingService vectorIndexingService,
            InMemoryVectorRagRetrievalStore vectorRetrievalStore,
            QdrantVectorRagIndexingStore qdrantVectorIndexingStore
    ) {
        this.ragProperties = ragProperties;
        this.documentLoader = documentLoader;
        this.chunkingService = chunkingService;
        this.retrievalStore = retrievalStore;
        this.vectorIndexingService = vectorIndexingService;
        this.vectorRetrievalStore = vectorRetrievalStore;
        this.qdrantVectorIndexingStore = qdrantVectorIndexingStore;
    }

    public RagCorpusService(
            RagProperties ragProperties,
            RagDocumentLoader documentLoader,
            RagChunkingService chunkingService,
            RagRetrievalStore retrievalStore,
            RagVectorIndexingService vectorIndexingService,
            InMemoryVectorRagRetrievalStore vectorRetrievalStore
    ) {
        this(
                ragProperties,
                documentLoader,
                chunkingService,
                retrievalStore,
                vectorIndexingService,
                vectorRetrievalStore,
                null
        );
    }

    /**
     * Ensures that the corpus is indexed. If it's already indexed, returns the current snapshot.
     * Otherwise, it builds the index.
     *
     * @return the current {@link CorpusSnapshot}
     */
    public synchronized CorpusSnapshot ensureIndexed() {
        return ensureIndexed(RagRetrievalTarget.fromRequestOrDefault(null, ragProperties));
    }

    /**
     * Ensures that the selected retrieval target has an up-to-date index.
     *
     * <p>If the corpus was already loaded for this target, the current snapshot
     * is reused. Otherwise, documents are reloaded and indexed into the target's
     * backing store.
     *
     * @param target retrieval target that must be query-ready
     * @return the current corpus snapshot
     */
    public synchronized CorpusSnapshot ensureIndexed(RagRetrievalTarget target) {
        CorpusSnapshot snapshot = snapshotRef.get();
        if (snapshot != null && indexedTargets.contains(target)) {
            return snapshot;
        }
        return rebuildIndex(target);
    }

    /**
     * Rebuilds the corpus index by reloading documents, re-chunking them, and updating the retrieval store.
     *
     * @return the new {@link CorpusSnapshot}
     */
    public synchronized CorpusSnapshot rebuildIndex() {
        return rebuildIndex(RagRetrievalTarget.fromRequestOrDefault(null, ragProperties));
    }

    /**
     * Rebuilds the corpus index for the selected retrieval target.
     *
     * @param target target whose backing store should receive the rebuilt index
     * @return the rebuilt corpus snapshot
     */
    public synchronized CorpusSnapshot rebuildIndex(RagRetrievalTarget target) {
        Path corpusRoot = ragProperties.resolvedCorpusRoot();
        List<RagDocument> documents = filterExcludedDocuments(documentLoader.loadMarkdownDocuments(corpusRoot));
        List<RagChunk> chunks = chunkingService.chunkDocuments(
                documents,
                ragProperties.maxChunkSize(),
                ragProperties.chunkOverlap()
        );
        if (target.retrievalMode() == RagRetrievalMode.VECTOR) {
            RagVectorIndexResult vectorIndex = vectorIndexingService.index(chunks);
            switch (target.vectorStoreMode()) {
                case IN_MEMORY -> vectorRetrievalStore.replaceAllEmbedded(vectorIndex.chunks());
                case QDRANT -> {
                    if (qdrantVectorIndexingStore == null) {
                        throw new RagVectorIndexingException("Qdrant vector indexing store is not configured.");
                    }
                    qdrantVectorIndexingStore.replaceAllEmbedded(vectorIndex, corpusRoot);
                }
            }
        } else {
            retrievalStore.replaceAll(chunks);
        }
        CorpusSnapshot snapshot = new CorpusSnapshot(corpusRoot, documents, chunks);
        snapshotRef.set(snapshot);
        indexedTargets.clear();
        indexedTargets.add(target);
        return snapshot;
    }

    private List<RagDocument> filterExcludedDocuments(List<RagDocument> documents) {
        Set<String> excludedPaths = ragProperties.excludedSourcePaths().stream()
                .map(RagCorpusService::normalizeSourcePath)
                .filter(path -> !path.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (excludedPaths.isEmpty()) {
            return documents;
        }
        return documents.stream()
                .filter(document -> !excludedPaths.contains(normalizeSourcePath(document.path().toString())))
                .toList();
    }

    private static String normalizeSourcePath(String sourcePath) {
        return sourcePath == null ? "" : sourcePath.replace('\\', '/').trim();
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
