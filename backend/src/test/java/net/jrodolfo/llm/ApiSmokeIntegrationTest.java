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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        FakeChatOrchestratorService() {
            super(null, null, null, null, null, null, new AppStorageProperties("data/sessions", "scripts/reports"));
        }

        @Override
        public ChatResponse chat(String message, String provider, String model, String sessionId) {
            return response;
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
}
