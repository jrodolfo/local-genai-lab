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
import net.jrodolfo.llm.rag.service.RagAnswerService;
import net.jrodolfo.llm.rag.service.RagChunkingService;
import net.jrodolfo.llm.rag.service.RagCorpusService;
import net.jrodolfo.llm.rag.service.RagDocumentLoader;
import net.jrodolfo.llm.rag.service.RagRetrievalService;
import net.jrodolfo.llm.rag.service.RagSessionService;
import net.jrodolfo.llm.service.ChatSessionMetadataService;
import net.jrodolfo.llm.service.FileChatSessionStore;
import net.jrodolfo.llm.service.SessionIdPolicy;
import net.jrodolfo.llm.rag.store.InMemoryRagVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @BeforeEach
    void setUp() throws Exception {
        Path docsRoot = tempDir.resolve("docs");
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

        RagProperties enabledProperties = new RagProperties(true, docsRoot.toString(), 180, 30, 3, "lexical");
        mockMvc = buildMockMvc(enabledProperties);

        RagProperties disabledProperties = new RagProperties(false, docsRoot.toString(), 180, 30, 3, "lexical");
        disabledMockMvc = buildMockMvc(disabledProperties);
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
                .andExpect(jsonPath("$.metadata.provider").value("ollama"));
    }

    @Test
    void statusReportsDisabledWhenFeatureIsOff() throws Exception {
        disabledMockMvc.perform(get("/api/rag/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.indexed").value(false));
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
        InMemoryRagVectorStore store = new InMemoryRagVectorStore();
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
                store
        );
        RagAnswerService answerService = new RagAnswerService(
                new ChatModelProviderRegistry(
                        new AppModelProperties("ollama"),
                        Map.of("ollama", new FakeProvider())
                ),
                new RagRetrievalService(properties, corpusService, store),
                new RagSessionService(sessionStore, new ChatSessionMetadataService(), sessionIdPolicy)
        );
        return MockMvcBuilders.standaloneSetup(new RagController(properties, corpusService, answerService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
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
}
