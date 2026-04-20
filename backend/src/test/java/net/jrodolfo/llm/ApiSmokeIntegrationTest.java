package net.jrodolfo.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
import net.jrodolfo.llm.dto.ChatRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatSessionDetailResponse;
import net.jrodolfo.llm.dto.ChatSessionImportResponse;
import net.jrodolfo.llm.dto.ChatSessionMessageResponse;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.ProviderStatusResponse;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ProviderPrompt;
import net.jrodolfo.llm.provider.StreamingChatResult;
import net.jrodolfo.llm.service.AvailableModelsService;
import net.jrodolfo.llm.service.ChatOrchestratorService;
import net.jrodolfo.llm.service.ChatSessionExportService;
import net.jrodolfo.llm.service.ChatSessionImportService;
import net.jrodolfo.llm.service.ChatSessionService;
import net.jrodolfo.llm.service.ProviderStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@SpringBootTest(properties = {
        "MCP_ENABLED=false",
        "APP_MODEL_PROVIDER=ollama"
})
@AutoConfigureMockMvc
@Import(ApiSmokeIntegrationTest.TestConfig.class)
class ApiSmokeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FakeChatOrchestratorService chatOrchestratorService;

    @Autowired
    private FakeAvailableModelsService availableModelsService;

    @Autowired
    private FakeProviderStatusService providerStatusService;

    @Autowired
    private FakeChatSessionService chatSessionService;

    @Autowired
    private FakeChatSessionImportService chatSessionImportService;

    @Test
    void modelsEndpointReturnsProviderAwarePayload() throws Exception {
        availableModelsService.response =
                new AvailableModelsResponse(
                        "ollama",
                        "ollama",
                        List.of("ollama", "bedrock"),
                        "llama3:8b",
                        List.of("llama3:8b", "mistral:7b")
                );
        providerStatusService.response = new ProviderStatusResponse("ollama", "ready", "Ollama is ready.");

        mockMvc.perform(get("/api/models").param("provider", "ollama"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.defaultProvider").value("ollama"))
                .andExpect(jsonPath("$.providers[0]").value("ollama"))
                .andExpect(jsonPath("$.models[0]").value("llama3:8b"));
    }

    @Test
    void chatEndpointReturnsAssistantResponseShape() throws Exception {
        chatOrchestratorService.response = new ChatResponse(
                "Recursion is when a function calls itself.",
                "llama3:8b",
                null,
                null,
                "session-1",
                null,
                new ModelProviderMetadata("ollama", "llama3:8b", "stop", 10, 20, 30, 40L, 35L, null, null)
        );

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ChatRequest(
                                "Explain recursion.",
                                "ollama",
                                "llama3:8b",
                                "session-1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Recursion is when a function calls itself."))
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.metadata.provider").value("ollama"))
                .andExpect(jsonPath("$.metadata.modelId").value("llama3:8b"));
    }

    @Test
    void streamingChatEndpointEmitsSseEventsAndPassesRequestFields() throws Exception {
        chatOrchestratorService.streamingSessionId = "stream-session-7";

        var mvcResult = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(new ChatRequest(
                                "Explain recursion.",
                                "bedrock",
                                "us.amazon.nova-pro-v1:0",
                                "stream-session-7"
                        ))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chat")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"start\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"delta\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"complete\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("stream-chunk")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"sessionId\":\"stream-session-7\"")));

        org.junit.jupiter.api.Assertions.assertEquals("Explain recursion.", chatOrchestratorService.lastMessage);
        org.junit.jupiter.api.Assertions.assertEquals("bedrock", chatOrchestratorService.lastProvider);
        org.junit.jupiter.api.Assertions.assertEquals("us.amazon.nova-pro-v1:0", chatOrchestratorService.lastModel);
        org.junit.jupiter.api.Assertions.assertEquals("stream-session-7", chatOrchestratorService.lastSessionId);
        org.junit.jupiter.api.Assertions.assertEquals("stream-chunk", chatOrchestratorService.lastAssistantResponse);
        org.junit.jupiter.api.Assertions.assertTrue(chatOrchestratorService.lastRequestId != null && !chatOrchestratorService.lastRequestId.isBlank());
    }

    @Test
    void importSessionEndpointAcceptsMultipartJson() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "session.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"sessionId\":\"session-123\"}".getBytes(StandardCharsets.UTF_8)
        );

        chatSessionImportService.response = new ChatSessionImportResponse("session-123", "Imported chat", "Summary", false, 2);

        mockMvc.perform(multipart("/api/sessions/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-123"))
                .andExpect(jsonPath("$.title").value("Imported chat"))
                .andExpect(jsonPath("$.messageCount").value(2));
    }

    @Test
    void exportSessionEndpointReturnsJsonAttachment() throws Exception {
        chatSessionService.session = new ChatSessionDetailResponse(
                "session-123",
                "Smoke Session",
                "Short summary",
                "llama3:8b",
                Instant.parse("2026-04-19T00:00:00Z"),
                Instant.parse("2026-04-19T00:05:00Z"),
                List.of(new ChatSessionMessageResponse(
                        "assistant",
                        "Hello from the saved session.",
                        null,
                        null,
                        new ModelProviderMetadata("ollama", "llama3:8b", "stop", null, null, null, 50L, null, null, null),
                        Instant.parse("2026-04-19T00:01:00Z")
                )),
                null
        );

        mockMvc.perform(get("/api/sessions/session-123/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"session-123.json\""))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sessionId").value("session-123"))
                .andExpect(jsonPath("$.messages[0].content").value("Hello from the saved session."))
                .andExpect(jsonPath("$.messages[0].metadata.provider").value("ollama"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeChatOrchestratorService fakeChatOrchestratorService() {
            return new FakeChatOrchestratorService();
        }

        @Bean
        @Primary
        FakeAvailableModelsService fakeAvailableModelsService() {
            return new FakeAvailableModelsService();
        }

        @Bean
        @Primary
        FakeProviderStatusService fakeProviderStatusService() {
            return new FakeProviderStatusService();
        }

        @Bean
        @Primary
        FakeChatSessionService fakeChatSessionService() {
            return new FakeChatSessionService();
        }

        @Bean
        @Primary
        FakeChatSessionExportService fakeChatSessionExportService() {
            return new FakeChatSessionExportService();
        }

        @Bean
        @Primary
        FakeChatSessionImportService fakeChatSessionImportService() {
            return new FakeChatSessionImportService();
        }
    }

    static final class FakeChatOrchestratorService extends ChatOrchestratorService {
        private ChatResponse response;
        private final ChatModelProvider streamingProvider = new FakeStreamingProvider();
        private String streamingSessionId = "stream-session-1";
        private String lastMessage;
        private String lastProvider;
        private String lastModel;
        private String lastSessionId;
        private String lastRequestId;
        private String lastAssistantResponse;

        FakeChatOrchestratorService() {
            super(null, null, null, null, null, null, new AppStorageProperties("data/sessions", "scripts/reports"));
        }

        @Override
        public ChatResponse chat(String message, String provider, String model, String sessionId) {
            this.lastMessage = message;
            this.lastProvider = provider;
            this.lastModel = model;
            this.lastSessionId = sessionId;
            return response;
        }

        @Override
        public ChatResponse chat(String message, String provider, String model, String sessionId, String requestId) {
            this.lastRequestId = requestId;
            return chat(message, provider, model, sessionId);
        }

        @Override
        public PreparedChat prepareChat(String message, String provider, String model, String sessionId, String requestId) {
            this.lastMessage = message;
            this.lastProvider = provider;
            this.lastModel = model;
            this.lastSessionId = sessionId;
            this.lastRequestId = requestId;
            return new PreparedChat(
                    streamingProvider,
                    ProviderPrompt.forPrompt("Explain recursion."),
                    model,
                    null,
                    null,
                    null,
                    ChatSession.create(streamingSessionId, model, Instant.parse("2026-04-19T00:00:00Z")),
                    null
            );
        }

        @Override
        public ChatSession completePreparedChat(PreparedChat preparedChat, String assistantResponse, ModelProviderMetadata providerMetadata, String requestId) {
            this.lastAssistantResponse = assistantResponse;
            this.lastRequestId = requestId;
            return preparedChat.session();
        }
    }

    static final class FakeAvailableModelsService extends AvailableModelsService {
        private AvailableModelsResponse response;

        FakeAvailableModelsService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public AvailableModelsResponse getAvailableModels(String provider) {
            return response;
        }
    }

    static final class FakeProviderStatusService extends ProviderStatusService {
        private ProviderStatusResponse response;

        FakeProviderStatusService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public ProviderStatusResponse getProviderStatus(String provider) {
            return response;
        }
    }

    static final class FakeChatSessionService extends ChatSessionService {
        private ChatSessionDetailResponse session;

        FakeChatSessionService() {
            super(null, null);
        }

        @Override
        public ChatSessionDetailResponse getSession(String sessionId) {
            return session;
        }
    }

    static final class FakeChatSessionExportService extends ChatSessionExportService {
    }

    static final class FakeChatSessionImportService extends ChatSessionImportService {
        private ChatSessionImportResponse response;

        FakeChatSessionImportService() {
            super(null, null, null, null);
        }

        @Override
        public ChatSessionImportResponse importSession(MultipartFile file) {
            return response;
        }
    }

    static final class FakeStreamingProvider implements ChatModelProvider {
        @Override
        public ChatResponse chat(ProviderPrompt prompt, String model, net.jrodolfo.llm.dto.ChatToolMetadata toolMetadata, Map<String, Object> toolResult, String sessionId, net.jrodolfo.llm.dto.PendingToolCallResponse pendingTool) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamingChatResult streamChat(ProviderPrompt prompt, String model, Consumer<String> tokenConsumer) {
            tokenConsumer.accept("stream-chunk");
            return new StreamingChatResult(
                    CompletableFuture.completedFuture(new ModelProviderMetadata("bedrock", model, "stop", 1, 2, 3, 4L, 5L, null, null)),
                    () -> { }
            );
        }

        @Override
        public String resolveModel(String model) {
            return model;
        }
    }
}
