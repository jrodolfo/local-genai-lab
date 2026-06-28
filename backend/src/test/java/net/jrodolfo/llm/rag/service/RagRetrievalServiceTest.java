package net.jrodolfo.llm.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.embedding.EmbeddingService;
import net.jrodolfo.llm.rag.embedding.EmbeddingVector;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagDocument;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.rag.qdrant.QdrantClient;
import net.jrodolfo.llm.rag.qdrant.QdrantPoint;
import net.jrodolfo.llm.rag.store.InMemoryLexicalRagRetrievalStore;
import net.jrodolfo.llm.rag.store.InMemoryVectorRagRetrievalStore;
import net.jrodolfo.llm.rag.store.QdrantVectorRagIndexingStore;
import net.jrodolfo.llm.rag.store.QdrantVectorRagRetrievalStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRetrievalServiceTest {

    @Test
    void retrieveReturnsMostRelevantChunksFirst() {
        RagRetrievalService retrievalService = retrievalService(3, List.of(
                document("architecture.md", "Architecture", "The provider registry selects Ollama, Bedrock, or Hugging Face for each request."),
                document("sessions.md", "Sessions", "Sessions are stored as local JSON files for persistence and export.")
        ));

        var matches = retrievalService.retrieve("How does the provider registry work?");

        assertEquals("architecture.md", matches.getFirst().chunk().sourcePath());
        assertEquals(1, matches.size());
    }

    @Test
    void partialOverlapRanksRelevantChunkFirst() {
        RagRetrievalService retrievalService = retrievalService(3, List.of(
                document("providers.md", "Providers", "Provider switching uses configured Ollama Bedrock and Hugging Face runtimes."),
                document("artifacts.md", "Artifacts", "Artifact preview shows report files and summary files from tool runs.")
        ));

        var matches = retrievalService.retrieve("configured provider runtimes");

        assertEquals("providers.md", matches.getFirst().chunk().sourcePath());
        assertEquals(1, matches.size());
    }

    @Test
    void stopWordHeavyQueryStillUsesMeaningfulTerms() {
        RagRetrievalService retrievalService = retrievalService(3, List.of(
                document("sessions.md", "Sessions", "Sessions are persisted as local JSON files and can be exported."),
                document("providers.md", "Providers", "Providers are selected at runtime from configured model backends.")
        ));

        var matches = retrievalService.retrieve("the and of to where are sessions in this app");

        assertEquals("sessions.md", matches.getFirst().chunk().sourcePath());
        assertEquals(1, matches.size());
    }

    @Test
    void javaVersionQueryRanksJavaSourcesAboveGenericProjectSources() {
        RagRetrievalService retrievalService = retrievalService(3, List.of(
                document(
                        "docs/troubleshooting.md",
                        "Troubleshooting",
                        "Java Version Warnings. This project targets Java 21 for the Spring Boot backend. Recommended fix: use Java 21 for this repo. Check java -version."
                ),
                document(
                        "docs/java-note.md",
                        "Java Note",
                        "Java 21."
                ),
                document(
                        "adr/0001-mcp-separate-typescript-runtime.md",
                        "ADR 0001: Keep MCP As A Separate TypeScript Runtime",
                        "The backend is implemented in Java, while the MCP server is implemented in TypeScript and runs as a separate local stdio process."
                ),
                document(
                        "adr/0008-use-curated-hugging-face-candidates-not-full-catalog.md",
                        "ADR 0008",
                        "The project uses a curated Hugging Face candidate list. A curated candidate list is simpler, stable, and aligned with the project's local lab goals."
                ),
                document(
                        "adr/0012-add-isolated-phase-1-rag-workspace-over-local-docs-corpus.md",
                        "ADR 0012",
                        """
                        Primary implementation:
                        - [RagController.java](../../backend/src/main/java/net/jrodolfo/llm/rag/controller/RagController.java)
                        - [RagAnswerService.java](../../backend/src/main/java/net/jrodolfo/llm/rag/service/RagAnswerService.java)
                        - [RagCorpusService.java](../../backend/src/main/java/net/jrodolfo/llm/rag/service/RagCorpusService.java)
                        The project adds a RAG workspace over local docs.
                        """
                ),
                document(
                        "docs/implementation-paths.md",
                        "Implementation Paths",
                        "Backend sources are located under backend/src/main/java/net/jrodolfo/llm and frontend sources are located under frontend/src/pages."
                )
        ));

        var matches = retrievalService.retrieve("What is the version of Java that this project is using?");

        assertEquals("docs/troubleshooting.md", matches.getFirst().chunk().sourcePath());
        assertTrue(matches.stream()
                .anyMatch(match -> "docs/java-note.md".equals(match.chunk().sourcePath())));
        assertTrue(matches.stream()
                .noneMatch(match -> "adr/0012-add-isolated-phase-1-rag-workspace-over-local-docs-corpus.md".equals(match.chunk().sourcePath())));
        assertTrue(matches.stream()
                .noneMatch(match -> "docs/implementation-paths.md".equals(match.chunk().sourcePath())));
        assertTrue(matches.stream()
                .noneMatch(match -> "adr/0001-mcp-separate-typescript-runtime.md".equals(match.chunk().sourcePath())));
    }

    @Test
    void javaVersionQueryFindsRealTroubleshootingChunkAfterChunking() throws IOException {
        String troubleshooting = Files.readString(Path.of("../docs/troubleshooting.md"));
        RagRetrievalService retrievalService = retrievalService(4, List.of(
                document("troubleshooting.md", "Troubleshooting", troubleshooting),
                document(
                        "adr/0012-add-isolated-phase-1-rag-workspace-over-local-docs-corpus.md",
                        "ADR 0012",
                        """
                        Primary implementation:
                        - [RagController.java](../../backend/src/main/java/net/jrodolfo/llm/rag/controller/RagController.java)
                        - [RagAnswerService.java](../../backend/src/main/java/net/jrodolfo/llm/rag/service/RagAnswerService.java)
                        The project adds a RAG workspace over local docs.
                        """
                )
        ));

        var matches = retrievalService.retrieve("What is the Java version used in this project?");

        assertTrue(matches.stream()
                .anyMatch(match -> "troubleshooting.md".equals(match.chunk().sourcePath())
                        && match.chunk().text().contains("Java 21")));
        assertTrue(matches.stream()
                .noneMatch(match -> "adr/0012-add-isolated-phase-1-rag-workspace-over-local-docs-corpus.md".equals(match.chunk().sourcePath())));
    }

    @Test
    void irrelevantQueryReturnsNoMatches() {
        RagRetrievalService retrievalService = retrievalService(3, List.of(
                document("sessions.md", "Sessions", "Sessions are persisted as local JSON files."),
                document("providers.md", "Providers", "Providers are selected at runtime.")
        ));

        var matches = retrievalService.retrieve("kubernetes ingress autoscaling cluster");

        assertEquals(0, matches.size());
    }

    @Test
    void topKLimitsReturnedMatches() {
        RagRetrievalService retrievalService = retrievalService(2, List.of(
                document("provider-registry.md", "Provider Registry", "Provider registry provider registry provider registry selects runtime models."),
                document("provider-status.md", "Provider Status", "Provider status checks provider health and provider model availability."),
                document("provider-docs.md", "Provider Docs", "Provider docs explain configured providers.")
        ));

        var matches = retrievalService.retrieve("provider");

        assertEquals(2, matches.size());
        List<String> sourcePaths = matches.stream()
                .map(match -> match.chunk().sourcePath())
                .toList();
        assertTrue(sourcePaths.contains("provider-registry.md"));
        assertTrue(sourcePaths.contains("provider-status.md"));
    }

    @Test
    void vectorModeRetrievesUsingQueryEmbedding() {
        RagProperties properties = new RagProperties(true, "docs", 220, 30, 2, "vector", "ollama", "nomic-embed-text");
        InMemoryLexicalRagRetrievalStore lexicalStore = new InMemoryLexicalRagRetrievalStore();
        InMemoryVectorRagRetrievalStore vectorStore = new InMemoryVectorRagRetrievalStore();
        KeywordEmbeddingService embeddingService = new KeywordEmbeddingService();
        RagCorpusService corpusService = new RagCorpusService(
                properties,
                new StubLoader(List.of(
                        document("architecture.md", "Architecture", "The provider registry selects Ollama Bedrock or Hugging Face."),
                        document("sessions.md", "Sessions", "Sessions are persisted as local JSON files.")
                )),
                new RagChunkingService(),
                lexicalStore,
                new RagVectorIndexingService(embeddingService, properties),
                vectorStore
        );
        RagRetrievalService retrievalService = new RagRetrievalService(
                properties,
                corpusService,
                lexicalStore,
                new RagVectorRetrievalService(embeddingService, vectorStore)
        );

        var matches = retrievalService.retrieve("How does provider selection work?");

        assertEquals("architecture.md", matches.getFirst().chunk().sourcePath());
        assertEquals(1, matches.size());
    }

    @Test
    void vectorModeDoesNotRetrieveExcludedEvaluationDocs() {
        RagProperties properties = new RagProperties(
                true,
                "docs",
                220,
                30,
                3,
                "vector",
                "ollama",
                "nomic-embed-text",
                List.of("rag-evaluation-guide.md", "rag-retrieval-evaluation-template.md")
        );
        InMemoryLexicalRagRetrievalStore lexicalStore = new InMemoryLexicalRagRetrievalStore();
        InMemoryVectorRagRetrievalStore vectorStore = new InMemoryVectorRagRetrievalStore();
        KeywordEmbeddingService embeddingService = new KeywordEmbeddingService();
        RagCorpusService corpusService = new RagCorpusService(
                properties,
                new StubLoader(List.of(
                        document(
                                "rag-evaluation-guide.md",
                                "RAG Evaluation Guide",
                                "Run this prompt in the RAG workspace: Where does conversation history live?"
                        ),
                        document(
                                "adr/0006-persist-sessions-as-local-json-files.md",
                                "ADR 0006",
                                "Conversation history lives in local JSON session files managed by the backend."
                        )
                )),
                new RagChunkingService(),
                lexicalStore,
                new RagVectorIndexingService(embeddingService, properties),
                vectorStore
        );
        RagRetrievalService retrievalService = new RagRetrievalService(
                properties,
                corpusService,
                lexicalStore,
                new RagVectorRetrievalService(embeddingService, vectorStore)
        );

        var matches = retrievalService.retrieve("Where does conversation history live?");

        assertEquals(1, matches.size());
        assertEquals("adr/0006-persist-sessions-as-local-json-files.md", matches.getFirst().chunk().sourcePath());
    }

    @Test
    void vectorModeRoutesToQdrantWhenConfigured() {
        RagProperties properties = new RagProperties(
                true,
                "docs",
                220,
                30,
                2,
                "vector",
                "qdrant",
                "http://localhost:6333",
                "local_genai_lab_docs",
                "ollama",
                "nomic-embed-text"
        );
        InMemoryLexicalRagRetrievalStore lexicalStore = new InMemoryLexicalRagRetrievalStore();
        InMemoryVectorRagRetrievalStore vectorStore = new InMemoryVectorRagRetrievalStore();
        KeywordEmbeddingService embeddingService = new KeywordEmbeddingService();
        RecordingQdrantClient indexingQdrantClient = new RecordingQdrantClient();
        RagCorpusService corpusService = new RagCorpusService(
                properties,
                new StubLoader(List.of(document("architecture.md", "Architecture", "Provider registry docs."))),
                new RagChunkingService(),
                lexicalStore,
                new RagVectorIndexingService(embeddingService, properties),
                vectorStore,
                new QdrantVectorRagIndexingStore(properties, indexingQdrantClient)
        );
        StubQdrantVectorStore qdrantVectorStore = new StubQdrantVectorStore();
        RagRetrievalService retrievalService = new RagRetrievalService(
                properties,
                corpusService,
                lexicalStore,
                new RagVectorRetrievalService(properties, embeddingService, vectorStore, qdrantVectorStore)
        );

        var matches = retrievalService.retrieve("How does provider selection work?");

        assertEquals(1, matches.size());
        assertEquals("qdrant.md", matches.getFirst().chunk().sourcePath());
        assertEquals(List.of(1.0, 0.0), qdrantVectorStore.queryVector);
        assertEquals(2, qdrantVectorStore.topK);
        assertEquals("local_genai_lab_docs", indexingQdrantClient.recreatedCollection);
        assertEquals(2, indexingQdrantClient.recreatedVectorSize);
        assertEquals(1, indexingQdrantClient.upsertedPoints.size());
        assertEquals("architecture.md", indexingQdrantClient.upsertedPoints.getFirst().payload().sourcePath());
    }

    @Test
    void invalidVectorStoreFailsClearly() {
        RagProperties properties = new RagProperties(
                true,
                "docs",
                220,
                30,
                2,
                "vector",
                "pinecone",
                "http://localhost:6333",
                "local_genai_lab_docs",
                "ollama",
                "nomic-embed-text"
        );
        InMemoryLexicalRagRetrievalStore lexicalStore = new InMemoryLexicalRagRetrievalStore();
        InMemoryVectorRagRetrievalStore vectorStore = new InMemoryVectorRagRetrievalStore();
        KeywordEmbeddingService embeddingService = new KeywordEmbeddingService();
        RagCorpusService corpusService = new RagCorpusService(
                properties,
                new StubLoader(List.of(document("architecture.md", "Architecture", "Provider registry docs."))),
                new RagChunkingService(),
                lexicalStore,
                new RagVectorIndexingService(embeddingService, properties),
                vectorStore
        );
        RagRetrievalService retrievalService = new RagRetrievalService(
                properties,
                corpusService,
                lexicalStore,
                new RagVectorRetrievalService(properties, embeddingService, vectorStore, null)
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> retrievalService.retrieve("provider"));

        assertEquals("Unsupported RAG vector store: pinecone. Supported stores: in-memory, qdrant.", exception.getMessage());
    }

    @Test
    void invalidRetrievalModeFailsClearly() {
        RagProperties properties = new RagProperties(true, "docs", 220, 30, 2, "semantic", "ollama", "nomic-embed-text");
        InMemoryLexicalRagRetrievalStore lexicalStore = new InMemoryLexicalRagRetrievalStore();
        InMemoryVectorRagRetrievalStore vectorStore = new InMemoryVectorRagRetrievalStore();
        KeywordEmbeddingService embeddingService = new KeywordEmbeddingService();
        RagCorpusService corpusService = new RagCorpusService(
                properties,
                new StubLoader(List.of(document("architecture.md", "Architecture", "Provider registry docs."))),
                new RagChunkingService(),
                lexicalStore,
                new RagVectorIndexingService(embeddingService, properties),
                vectorStore
        );
        RagRetrievalService retrievalService = new RagRetrievalService(
                properties,
                corpusService,
                lexicalStore,
                new RagVectorRetrievalService(embeddingService, vectorStore)
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> retrievalService.retrieve("provider"));

        assertEquals("Unsupported RAG retrieval mode: semantic. Supported modes: lexical, vector.", exception.getMessage());
    }

    private static RagRetrievalService retrievalService(int topK, List<RagDocument> documents) {
        RagProperties properties = new RagProperties(true, "docs", 220, 30, topK, "lexical", "ollama", "nomic-embed-text");
        InMemoryLexicalRagRetrievalStore store = new InMemoryLexicalRagRetrievalStore();
        InMemoryVectorRagRetrievalStore vectorStore = new InMemoryVectorRagRetrievalStore();
        KeywordEmbeddingService embeddingService = new KeywordEmbeddingService();
        RagCorpusService corpusService = new RagCorpusService(
                properties,
                new StubLoader(documents),
                new RagChunkingService(),
                store,
                new RagVectorIndexingService(embeddingService, properties),
                vectorStore
        );
        return new RagRetrievalService(properties, corpusService, store, new RagVectorRetrievalService(embeddingService, vectorStore));
    }

    private static RagDocument document(String path, String title, String content) {
        return new RagDocument(Path.of(path), title, content);
    }

    private static final class StubLoader extends RagDocumentLoader {
        private final List<RagDocument> documents;

        private StubLoader(List<RagDocument> documents) {
            this.documents = documents;
        }

        @Override
        public List<RagDocument> loadMarkdownDocuments(Path corpusRoot) {
            return documents;
        }
    }

    private static final class KeywordEmbeddingService implements EmbeddingService {

        @Override
        public EmbeddingVector embed(String text) {
            String normalized = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("provider") || normalized.contains("ollama") || normalized.contains("bedrock")) {
                return new EmbeddingVector("nomic-embed-text", List.of(1.0, 0.0));
            }
            if (normalized.contains("session")
                    || normalized.contains("json")
                    || normalized.contains("conversation")
                    || normalized.contains("history")) {
                return new EmbeddingVector("nomic-embed-text", List.of(0.0, 1.0));
            }
            return new EmbeddingVector("nomic-embed-text", List.of(0.0, 0.0));
        }
    }

    private static final class StubQdrantVectorStore extends QdrantVectorRagRetrievalStore {
        private List<Double> queryVector;
        private int topK;

        private StubQdrantVectorStore() {
            super(null, null);
        }

        @Override
        public List<RagMatch> searchByVector(List<Double> queryVector, int topK) {
            this.queryVector = queryVector;
            this.topK = topK;
            return List.of(new RagMatch(new RagChunk("qdrant.md#0", "qdrant.md", "Qdrant", "Qdrant result."), 0.93));
        }
    }

    private static final class RecordingQdrantClient extends QdrantClient {
        private String recreatedCollection;
        private int recreatedVectorSize;
        private List<QdrantPoint> upsertedPoints = List.of();

        private RecordingQdrantClient() {
            super(new ObjectMapper());
        }

        @Override
        public void recreateCollection(String qdrantUrl, String collectionName, int vectorSize) {
            this.recreatedCollection = collectionName;
            this.recreatedVectorSize = vectorSize;
        }

        @Override
        public void upsertPoints(String qdrantUrl, String collectionName, List<QdrantPoint> points) {
            this.upsertedPoints = points;
        }
    }
}
