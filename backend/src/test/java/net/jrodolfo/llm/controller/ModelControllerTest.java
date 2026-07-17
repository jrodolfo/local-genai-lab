package net.jrodolfo.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClientException;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
import net.jrodolfo.llm.dto.ProviderStatusResponse;
import net.jrodolfo.llm.service.AvailableModelsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ModelController(new FakeAvailableModelsService(), new FakeProviderStatusService()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    @Test
    void listAvailableModelsReturnsProviderAwarePayload() throws Exception {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.defaultProvider").value("ollama"))
                .andExpect(jsonPath("$.providers[0]").value("bedrock"))
                .andExpect(jsonPath("$.providers[1]").value("ollama"))
                .andExpect(jsonPath("$.defaultModel").value("llama3:8b"))
                .andExpect(jsonPath("$.models[0]").value("llama3:8b"))
                .andExpect(jsonPath("$.models[1]").value("mistral:7b"));
    }

    @Test
    void ollamaDiscoveryErrorsMapToBadGateway() throws Exception {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ModelController(new ErrorAvailableModelsService(), new FakeProviderStatusService()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();

        mockMvc.perform(get("/api/models"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("ollama unavailable"));
    }

    @Test
    void invalidProviderMapsToBadRequest() throws Exception {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ModelController(new InvalidProviderAvailableModelsService(), new FakeProviderStatusService()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();

        mockMvc.perform(get("/api/models").param("provider", "unsupported"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported provider"));
    }

    @Test
    void bedrockDiscoveryErrorsMapToBadGateway() throws Exception {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ModelController(new BedrockErrorAvailableModelsService(), new FakeProviderStatusService()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();

        mockMvc.perform(get("/api/models"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("bedrock unavailable"));
    }

    @Test
    void providerStatusReturnsCompactStatusPayload() throws Exception {
        mockMvc.perform(get("/api/models/status").param("provider", "bedrock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("bedrock"))
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.message").value("Bedrock is configured and ready."));
    }

    private static final class FakeAvailableModelsService extends AvailableModelsService {
        private FakeAvailableModelsService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public AvailableModelsResponse getAvailableModels(String provider) {
            return new AvailableModelsResponse("ollama", "ollama", List.of("bedrock", "ollama"), "llama3:8b", List.of("llama3:8b", "mistral:7b"));
        }
    }

    private static final class FakeProviderStatusService extends net.jrodolfo.llm.service.ProviderStatusService {
        private FakeProviderStatusService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public ProviderStatusResponse getProviderStatus(String provider) {
            return new ProviderStatusResponse("bedrock", "ready", "Bedrock is configured and ready.");
        }
    }

    private static final class ErrorAvailableModelsService extends AvailableModelsService {
        private ErrorAvailableModelsService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public AvailableModelsResponse getAvailableModels(String provider) {
            throw new OllamaClientException("ollama unavailable");
        }
    }

    private static final class BedrockErrorAvailableModelsService extends AvailableModelsService {
        private BedrockErrorAvailableModelsService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public AvailableModelsResponse getAvailableModels(String provider) {
            throw new ModelDiscoveryException("bedrock unavailable");
        }
    }

    private static final class InvalidProviderAvailableModelsService extends AvailableModelsService {
        private InvalidProviderAvailableModelsService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public AvailableModelsResponse getAvailableModels(String provider) {
            throw new net.jrodolfo.llm.service.InvalidProviderException("unsupported provider");
        }
    }
}
