package net.jrodolfo.llm.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import net.jrodolfo.llm.provider.ProviderPrompt;
import net.jrodolfo.llm.provider.StreamingChatResult;
import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.embedding.EmbeddingService;
import net.jrodolfo.llm.rag.embedding.EmbeddingVector;
import net.jrodolfo.llm.rag.qdrant.QdrantStatus;
import net.jrodolfo.llm.rag.qdrant.QdrantStatusService;
import net.jrodolfo.llm.rag.service.RagAnswerService;
import net.jrodolfo.llm.rag.service.RagChunkingService;
import net.jrodolfo.llm.rag.service.RagCorpusService;
import net.jrodolfo.llm.rag.service.RagDocumentLoader;
import net.jrodolfo.llm.rag.service.RagRetrievalService;
import net.jrodolfo.llm.rag.service.RagRetrievalOptions;
import net.jrodolfo.llm.rag.service.RagSessionService;
import net.jrodolfo.llm.rag.service.RagVectorIndexingException;
import net.jrodolfo.llm.rag.service.RagVectorIndexingService;
import net.jrodolfo.llm.rag.service.RagVectorRetrievalService;
import net.jrodolfo.llm.service.ChatSessionMetadataService;
import net.jrodolfo.llm.service.FileChatSessionStore;
import net.jrodolfo.llm.service.SessionIdPolicy;
import net.jrodolfo.llm.rag.store.InMemoryLexicalRagRetrievalStore;
import net.jrodolfo.llm.rag.store.InMemoryVectorRagRetrievalStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RagControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private MockMvc disabledMockMvc;
    private MockMvc vectorMockMvc;
    private MockMvc qdrantReachableMockMvc;
    private MockMvc qdrantUnavailableMockMvc;
    private MockMvc invalidModeMockMvc;
    private Path docsRoot;

    @BeforeEach
    void setUp() throws Exception {
        docsRoot = tempDir.resolve("docs");
        Files.createDirectories(docsRoot);
        Files.writeString(docsRoot.resolve("architecture.md"), """
                # Architecture

                The provider registry selects Ollama, Bedrock, or Hugging Face at request time.

                MCP remains separate from the backend chat orchestration.
                """);
        Files.writeString(docsRoot.resolve("sessions.md"), """
                # Sessions

                Sessions are stored as local JSON files so they can be reopened, exported, and imported.
                """);

        RagProperties enabledProperties = new RagProperties(true, docsRoot.toString(), 180, 30, 3, "lexical", "ollama", "nomic-embed-text");
        mockMvc = buildMockMvc(enabledProperties);

        RagProperties disabledProperties = new RagProperties(false, docsRoot.toString(), 180, 30, 3, "lexical", "ollama", "nomic-embed-text");
        disabledMockMvc = buildMockMvc(disabledProperties);

        RagProperties vectorProperties = new RagProperties(true, docsRoot.toString(), 180, 30, 3, "vector", "ollama", "nomic-embed-text");
        vectorMockMvc = buildMockMvc(vectorProperties);

        RagProperties qdrantConfigProperties = new RagProperties(
                true,
                docsRoot.toString(),
                180,
                30,
                3,
                "vector",
                "qdrant",
                "http://localhost:6333",
                "local_genai_lab_docs",
                "ollama",
                "nomic-embed-text"
        );
        qdrantReachableMockMvc = buildMockMvc(qdrantConfigProperties, QdrantStatus.collectionPresent("local_genai_lab_docs", 123L));
        qdrantUnavailableMockMvc = buildMockMvc(qdrantConfigProperties, QdrantStatus.unavailable("http://localhost:6333"));

        RagProperties invalidModeProperties = new RagProperties(true, docsRoot.toString(), 180, 30, 3, "semantic", "ollama", "nomic-embed-text");
        invalidModeMockMvc = buildMockMvc(invalidModeProperties);
    }

    @Test
    void queryReturnsAnswerAndSources() throws Exception {
        mockMvc.perform(post("/api/rag/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "question": "How does provider selection work?",
                                  "provider": "ollama",
                                  "model": "llama3:8b"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsString("provider registry")))
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.model").value("llama3:8b"))
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.sources[0].sourcePath").value("architecture.md"))
                .andExpect(jsonPath("$.metadata.provider").value("ollama"))
                .andExpect(jsonPath("$.ragRetrieval.retrievalMode").value("lexical"))
                .andExpect(jsonPath("$.ragRetrieval.vectorStore").value("in-memory"))
                .andExpect(jsonPath("$.ragRetrieval.retrievalTarget").value("lexical:in-memory"))
                .andExpect(jsonPath("$.ragTiming.retrievalDurationMs").isNumber())
                .andExpect(jsonPath("$.ragTiming.providerDurationMs").isNumber())
                .andExpect(jsonPath("$.ragTiming.totalDurationMs").isNumber());
    }

    @Test
    void statusReportsDisabledWhenFeatureIsOff() throws Exception {
        disabledMockMvc.perform(get("/api/rag/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.indexed").value(false))
                .andExpect(jsonPath("$.retrievalMode").value("lexical"))
                .andExpect(jsonPath("$.retrievalStore").value("in-memory"))
                .andExpect(jsonPath("$.vectorStore").value("in-memory"))
                .andExpect(jsonPath("$.qdrantUrl").value("http://localhost:6333"))
                .andExpect(jsonPath("$.qdrantCollection").value("local_genai_lab_docs"))
                .andExpect(jsonPath("$.qdrantRequired").value(false))
                .andExpect(jsonPath("$.qdrantReachable").doesNotExist())
                .andExpect(jsonPath("$.qdrantCollectionExists").doesNotExist())
                .andExpect(jsonPath("$.qdrantPointCount").doesNotExist())
                .andExpect(jsonPath("$.qdrantStatusMessage").value("Qdrant is not required for the current RAG configuration."))
                .andExpect(jsonPath("$.embeddingProvider").value("ollama"))
                .andExpect(jsonPath("$.embeddingModel").value("nomic-embed-text"))
                .andExpect(jsonPath("$.retrievalTargets[0].value").value("lexical:in-memory"))
                .andExpect(jsonPath("$.retrievalTargets[0].available").value(false))
                .andExpect(jsonPath("$.retrievalTargets[1].value").value("vector:in-memory"))
                .andExpect(jsonPath("$.retrievalTargets[1].available").value(false))
                .andExpect(jsonPath("$.retrievalTargets[2].value").value("vector:qdrant"))
                .andExpect(jsonPath("$.retrievalTargets[2].available").value(false));
    }

    @Test
    void vectorModeStatusReportsVectorStore() throws Exception {
        vectorMockMvc.perform(get("/api/rag/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrievalMode").value("vector"))
                .andExpect(jsonPath("$.retrievalStore").value("in-memory-vector"))
                .andExpect(jsonPath("$.vectorStore").value("in-memory"))
                .andExpect(jsonPath("$.qdrantRequired").value(false))
                .andExpect(jsonPath("$.qdrantReachable").doesNotExist())
                .andExpect(jsonPath("$.qdrantCollectionExists").doesNotExist())
                .andExpect(jsonPath("$.qdrantPointCount").doesNotExist())
                .andExpect(jsonPath("$.embeddingProvider").value("ollama"))
                .andExpect(jsonPath("$.embeddingModel").value("nomic-embed-text"))
                .andExpect(jsonPath("$.retrievalTargets[0].value").value("lexical:in-memory"))
                .andExpect(jsonPath("$.retrievalTargets[0].available").value(true))
                .andExpect(jsonPath("$.retrievalTargets[0].ready").value(true))
                .andExpect(jsonPath("$.retrievalTargets[1].value").value("vector:in-memory"))
                .andExpect(jsonPath("$.retrievalTargets[1].available").value(true))
                .andExpect(jsonPath("$.retrievalTargets[1].ready").value(true))
                .andExpect(jsonPath("$.retrievalTargets[2].value").value("vector:qdrant"))
                .andExpect(jsonPath("$.retrievalTargets[2].available").value(false))
                .andExpect(jsonPath("$.retrievalTargets[2].ready").value(false));
    }

    @Test
    void qdrantConfigStatusReportsReachableQdrantWithoutChangingCurrentRetrievalStore() throws Exception {
        qdrantReachableMockMvc.perform(get("/api/rag/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrievalMode").value("vector"))
                .andExpect(jsonPath("$.retrievalStore").value("in-memory-vector"))
                .andExpect(jsonPath("$.vectorStore").value("qdrant"))
                .andExpect(jsonPath("$.qdrantUrl").value("http://localhost:6333"))
                .andExpect(jsonPath("$.qdrantCollection").value("local_genai_lab_docs"))
                .andExpect(jsonPath("$.qdrantRequired").value(true))
                .andExpect(jsonPath("$.qdrantReachable").value(true))
                .andExpect(jsonPath("$.qdrantCollectionExists").value(true))
                .andExpect(jsonPath("$.qdrantPointCount").value(123))
                .andExpect(jsonPath("$.qdrantStatusMessage").value("Qdrant collection local_genai_lab_docs is present with 123 points."))
                .andExpect(jsonPath("$.retrievalTargets[2].value").value("vector:qdrant"))
                .andExpect(jsonPath("$.retrievalTargets[2].label").value("Vector - Qdrant"))
                .andExpect(jsonPath("$.retrievalTargets[2].available").value(true))
                .andExpect(jsonPath("$.retrievalTargets[2].ready").value(true))
                .andExpect(jsonPath("$.retrievalTargets[2].pointCount").value(123))
                .andExpect(jsonPath("$.retrievalTargets[2].message").value("Ready. Qdrant collection local_genai_lab_docs has 123 points."));
    }

    @Test
    void qdrantConfigStatusReportsUnavailableQdrantWithoutChangingCurrentRetrievalStore() throws Exception {
        qdrantUnavailableMockMvc.perform(get("/api/rag/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrievalMode").value("vector"))
                .andExpect(jsonPath("$.retrievalStore").value("in-memory-vector"))
                .andExpect(jsonPath("$.vectorStore").value("qdrant"))
                .andExpect(jsonPath("$.qdrantRequired").value(true))
                .andExpect(jsonPath("$.qdrantReachable").value(false))
                .andExpect(jsonPath("$.qdrantCollectionExists").doesNotExist())
                .andExpect(jsonPath("$.qdrantPointCount").doesNotExist())
                .andExpect(jsonPath("$.qdrantStatusMessage").value("Qdrant is not reachable at http://localhost:6333."))
                .andExpect(jsonPath("$.retrievalTargets[2].value").value("vector:qdrant"))
                .andExpect(jsonPath("$.retrievalTargets[2].label").value("Vector - Qdrant Unavailable"))
                .andExpect(jsonPath("$.retrievalTargets[2].available").value(false))
                .andExpect(jsonPath("$.retrievalTargets[2].ready").value(false))
                .andExpect(jsonPath("$.retrievalTargets[2].message").value("Qdrant is not reachable at http://localhost:6333. Start Qdrant before selecting this target."));
    }

    @Test
    void qdrantConfigStatusReportsMissingCollection() throws Exception {
        qdrantMockMvc(QdrantStatus.collectionMissing("local_genai_lab_docs"))
                .perform(get("/api/rag/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vectorStore").value("qdrant"))
                .andExpect(jsonPath("$.qdrantRequired").value(true))
                .andExpect(jsonPath("$.qdrantReachable").value(true))
                .andExpect(jsonPath("$.qdrantCollectionExists").value(false))
                .andExpect(jsonPath("$.qdrantPointCount").doesNotExist())
                .andExpect(jsonPath("$.qdrantStatusMessage").value("Qdrant collection local_genai_lab_docs is missing. Rebuild the index."))
                .andExpect(jsonPath("$.retrievalTargets[2].value").value("vector:qdrant"))
                .andExpect(jsonPath("$.retrievalTargets[2].available").value(false))
                .andExpect(jsonPath("$.retrievalTargets[2].ready").value(false))
                .andExpect(jsonPath("$.retrievalTargets[2].message").value("Qdrant collection local_genai_lab_docs is missing. Rebuild the index."));
    }

    @Test
    void vectorModeQueryReturnsAnswerAndSources() throws Exception {
        vectorMockMvc.perform(post("/api/rag/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "question": "How does provider selection work?",
                                  "provider": "ollama",
                                  "model": "llama3:8b"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsString("provider registry")))
                .andExpect(jsonPath("$.sources[0].sourcePath").value("architecture.md"))
                .andExpect(jsonPath("$.ragRetrieval.retrievalMode").value("vector"))
                .andExpect(jsonPath("$.ragRetrieval.vectorStore").value("in-memory"))
                .andExpect(jsonPath("$.ragRetrieval.retrievalTarget").value("vector:in-memory"))
                .andExpect(jsonPath("$.ragTiming.retrievalDurationMs").isNumber())
                .andExpect(jsonPath("$.ragTiming.providerDurationMs").isNumber())
                .andExpect(jsonPath("$.ragTiming.totalDurationMs").isNumber());
    }

    @Test
    void queryCanOverrideConfiguredRetrievalModePerRequest() throws Exception {
        vectorMockMvc.perform(post("/api/rag/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "question": "How does provider selection work?",
                                  "provider": "ollama",
                                  "model": "llama3:8b",
                                  "retrievalMode": "lexical",
                                  "vectorStore": "in-memory"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsString("provider registry")))
                .andExpect(jsonPath("$.sources[0].sourcePath").value("architecture.md"))
                .andExpect(jsonPath("$.ragRetrieval.retrievalMode").value("lexical"))
                .andExpect(jsonPath("$.ragRetrieval.retrievalTarget").value("lexical:in-memory"));
    }

    @Test
    void indexCanOverrideConfiguredRetrievalModePerRequest() throws Exception {
        mockMvc.perform(post("/api/rag/index")
                        .contentType("application/json")
                        .content("""
                                {
                                  "retrievalMode": "vector",
                                  "vectorStore": "in-memory"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retrievalMode").value("vector"));
    }

    @Test
    void invalidRetrievalModeReturnsBadRequest() throws Exception {
        invalidModeMockMvc.perform(post("/api/rag/index"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported RAG retrieval mode: semantic. Supported modes: lexical, vector."));
    }

    @Test
    void invalidRequestVectorStoreReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/rag/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "question": "How does provider selection work?",
                                  "retrievalMode": "vector",
                                  "vectorStore": "pinecone"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported RAG vector store: pinecone. Supported stores: in-memory, qdrant."));
    }

    @Test
    void vectorIndexingFailureReturnsClearBadRequest() throws Exception {
        qdrantMockMvcWithFailingIndex().perform(post("/api/rag/index"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Failed to index RAG chunks in Qdrant."));
    }

    @Test
    void queryFailsWhenFeatureIsDisabled() throws Exception {
        disabledMockMvc.perform(post("/api/rag/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "question": "How does provider selection work?"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("RAG is disabled."));
    }

    private MockMvc buildMockMvc(RagProperties properties) {
        return buildMockMvc(properties, QdrantStatus.notRequired());
    }

    private MockMvc buildMockMvc(RagProperties properties, QdrantStatus qdrantStatus) {
        InMemoryLexicalRagRetrievalStore store = new InMemoryLexicalRagRetrievalStore();
        InMemoryVectorRagRetrievalStore vectorStore = new InMemoryVectorRagRetrievalStore();
        KeywordEmbeddingService embeddingService = new KeywordEmbeddingService();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        SessionIdPolicy sessionIdPolicy = new SessionIdPolicy();
        FileChatSessionStore sessionStore = new FileChatSessionStore(
                objectMapper,
                new AppStorageProperties(tempDir.resolve("sessions").toString(), tempDir.resolve("reports").toString()),
                sessionIdPolicy
        );
        RagCorpusService corpusService = new RagCorpusService(
                properties,
                new RagDocumentLoader(),
                new RagChunkingService(),
                store,
                new RagVectorIndexingService(embeddingService, properties),
                vectorStore
        );
        RagAnswerService answerService = new RagAnswerService(
                new ChatModelProviderRegistry(
                        new AppModelProperties("ollama"),
                        Map.of("ollama", new FakeProvider())
                ),
                new RagRetrievalService(properties, corpusService, store, new RagVectorRetrievalService(embeddingService, vectorStore)),
                new RagSessionService(sessionStore, new ChatSessionMetadataService(), sessionIdPolicy)
        );
        QdrantStatusService qdrantStatusService = new QdrantStatusService() {
            @Override
            public QdrantStatus status(RagProperties ragProperties) {
                return qdrantStatus;
            }
        };
        return MockMvcBuilders.standaloneSetup(new RagController(properties, corpusService, answerService, qdrantStatusService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private MockMvc qdrantMockMvc(QdrantStatus qdrantStatus) {
        return buildMockMvc(
                new RagProperties(
                        true,
                        docsRoot.toString(),
                        180,
                        30,
                        3,
                        "vector",
                        "qdrant",
                        "http://localhost:6333",
                        "local_genai_lab_docs",
                        "ollama",
                        "nomic-embed-text"
                ),
                qdrantStatus
        );
    }

    private MockMvc qdrantMockMvcWithFailingIndex() {
        RagProperties properties = new RagProperties(
                true,
                docsRoot.toString(),
                180,
                30,
                3,
                "vector",
                "qdrant",
                "http://localhost:6333",
                "local_genai_lab_docs",
                "ollama",
                "nomic-embed-text"
        );
        InMemoryLexicalRagRetrievalStore store = new InMemoryLexicalRagRetrievalStore();
        InMemoryVectorRagRetrievalStore vectorStore = new InMemoryVectorRagRetrievalStore();
        KeywordEmbeddingService embeddingService = new KeywordEmbeddingService();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        SessionIdPolicy sessionIdPolicy = new SessionIdPolicy();
        FileChatSessionStore sessionStore = new FileChatSessionStore(
                objectMapper,
                new AppStorageProperties(tempDir.resolve("sessions").toString(), tempDir.resolve("reports").toString()),
                sessionIdPolicy
        );
        RagCorpusService corpusService = new FailingIndexRagCorpusService(
                properties,
                new RagDocumentLoader(),
                new RagChunkingService(),
                store,
                new RagVectorIndexingService(embeddingService, properties),
                vectorStore
        );
        RagAnswerService answerService = new RagAnswerService(
                new ChatModelProviderRegistry(
                        new AppModelProperties("ollama"),
                        Map.of("ollama", new FakeProvider())
                ),
                new RagRetrievalService(properties, corpusService, store, new RagVectorRetrievalService(embeddingService, vectorStore)),
                new RagSessionService(sessionStore, new ChatSessionMetadataService(), sessionIdPolicy)
        );
        return MockMvcBuilders.standaloneSetup(new RagController(
                        properties,
                        corpusService,
                        answerService,
                        new QdrantStatusService() {
                            @Override
                            public QdrantStatus status(RagProperties ragProperties) {
                                return QdrantStatus.collectionPresent("local_genai_lab_docs", 123L);
                            }
                        }
                ))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class FailingIndexRagCorpusService extends RagCorpusService {

        private FailingIndexRagCorpusService(
                RagProperties ragProperties,
                RagDocumentLoader documentLoader,
                RagChunkingService chunkingService,
                InMemoryLexicalRagRetrievalStore retrievalStore,
                RagVectorIndexingService vectorIndexingService,
                InMemoryVectorRagRetrievalStore vectorRetrievalStore
        ) {
            super(ragProperties, documentLoader, chunkingService, retrievalStore, vectorIndexingService, vectorRetrievalStore);
        }

        @Override
        public synchronized CorpusSnapshot rebuildIndex() {
            throw new RagVectorIndexingException("Failed to index RAG chunks in Qdrant.");
        }

        @Override
        public synchronized CorpusSnapshot rebuildIndex(RagRetrievalOptions options) {
            throw new RagVectorIndexingException("Failed to index RAG chunks in Qdrant.");
        }
    }

    private static final class FakeProvider implements ChatModelProvider {

        @Override
        public ChatResponse chat(
                ProviderPrompt prompt,
                String model,
                ChatToolMetadata toolMetadata,
                Map<String, Object> toolResult,
                String sessionId,
                PendingToolCallResponse pendingTool
        ) {
            return new ChatResponse(
                    "The provider registry handles provider selection per request based on the selected provider and model.",
                    model,
                    null,
                    null,
                    null,
                    null,
                    new ModelProviderMetadata("ollama", model, null, null, null, null, null, null, null, null)
            );
        }

        @Override
        public StreamingChatResult streamChat(ProviderPrompt prompt, String model, Consumer<String> tokenConsumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String resolveModel(String model) {
            return model == null || model.isBlank() ? "llama3:8b" : model;
        }
    }

    private static final class KeywordEmbeddingService implements EmbeddingService {

        @Override
        public EmbeddingVector embed(String text) {
            String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
            if (normalized.contains("provider") || normalized.contains("ollama") || normalized.contains("bedrock")) {
                return new EmbeddingVector("nomic-embed-text", List.of(1.0, 0.0));
            }
            if (normalized.contains("session") || normalized.contains("json")) {
                return new EmbeddingVector("nomic-embed-text", List.of(0.0, 1.0));
            }
            return new EmbeddingVector("nomic-embed-text", List.of(0.0, 0.0));
        }
    }
}
