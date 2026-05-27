package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagDocument;
import net.jrodolfo.llm.rag.store.RagVectorStore;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RagCorpusService {

    private final RagProperties ragProperties;
    private final RagDocumentLoader documentLoader;
    private final RagChunkingService chunkingService;
    private final RagVectorStore vectorStore;
    private final AtomicReference<CorpusSnapshot> snapshotRef = new AtomicReference<>();

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

    public synchronized CorpusSnapshot ensureIndexed() {
        CorpusSnapshot snapshot = snapshotRef.get();
        if (snapshot != null) {
            return snapshot;
        }
        return rebuildIndex();
    }

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

    public CorpusSnapshot snapshot() {
        return snapshotRef.get();
    }

    public record CorpusSnapshot(
            Path corpusRoot,
            List<RagDocument> documents,
            List<RagChunk> chunks
    ) {
    }
}
