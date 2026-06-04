package net.jrodolfo.llm.rag.store;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagEmbeddedChunk;
import net.jrodolfo.llm.rag.model.RagVectorIndexResult;
import net.jrodolfo.llm.rag.qdrant.QdrantClient;
import net.jrodolfo.llm.rag.qdrant.QdrantClientException;
import net.jrodolfo.llm.rag.qdrant.QdrantPoint;
import net.jrodolfo.llm.rag.qdrant.QdrantPointPayload;
import net.jrodolfo.llm.rag.service.RagVectorIndexingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Writes an embedded RAG corpus index to Qdrant.
 */
@Component
public class QdrantVectorRagIndexingStore {

    private final RagProperties ragProperties;
    private final QdrantClient qdrantClient;
    private final Clock clock;

    @Autowired
    public QdrantVectorRagIndexingStore(RagProperties ragProperties, QdrantClient qdrantClient) {
        this(ragProperties, qdrantClient, Clock.systemUTC());
    }

    QdrantVectorRagIndexingStore(RagProperties ragProperties, QdrantClient qdrantClient, Clock clock) {
        this.ragProperties = ragProperties;
        this.qdrantClient = qdrantClient;
        this.clock = clock;
    }

    /**
     * Recreates the configured Qdrant collection and writes all embedded chunks.
     *
     * @param indexResult embedded chunks and vector metadata
     * @param corpusRoot  indexed corpus root
     */
    public void replaceAllEmbedded(RagVectorIndexResult indexResult, Path corpusRoot) {
        try {
            qdrantClient.recreateCollection(
                    ragProperties.qdrantUrl(),
                    ragProperties.qdrantCollection(),
                    indexResult.vectorDimension()
            );
            qdrantClient.upsertPoints(
                    ragProperties.qdrantUrl(),
                    ragProperties.qdrantCollection(),
                    toPoints(indexResult, corpusRoot)
            );
        } catch (QdrantClientException ex) {
            throw new RagVectorIndexingException("Failed to index RAG chunks in Qdrant.", ex);
        }
    }

    private List<QdrantPoint> toPoints(RagVectorIndexResult indexResult, Path corpusRoot) {
        String indexedAt = Instant.now(clock).toString();
        String normalizedCorpusRoot = corpusRoot == null ? "" : corpusRoot.toString();
        return indexResult.chunks().stream()
                .map(chunk -> toPoint(indexResult, chunk, normalizedCorpusRoot, indexedAt))
                .toList();
    }

    private QdrantPoint toPoint(
            RagVectorIndexResult indexResult,
            RagEmbeddedChunk chunk,
            String corpusRoot,
            String indexedAt
    ) {
        return new QdrantPoint(
                qdrantPointId(chunk.chunkId()),
                chunk.vector(),
                new QdrantPointPayload(
                        chunk.sourcePath(),
                        chunk.chunkId(),
                        chunk.title(),
                        chunk.text(),
                        corpusRoot,
                        indexResult.embeddingProvider(),
                        chunk.embeddingModel(),
                        indexedAt,
                        contentHash(chunk)
                )
        );
    }

    private static String qdrantPointId(String chunkId) {
        return UUID.nameUUIDFromBytes(chunkId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String contentHash(RagEmbeddedChunk chunk) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(chunk.chunkId().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(chunk.sourcePath().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(chunk.text().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
