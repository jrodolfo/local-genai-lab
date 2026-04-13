package net.jrodolfo.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClientException;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
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
                .standaloneSetup(new ModelController(new FakeAvailableModelsService()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
    }

    @Test
    void listAvailableModelsReturnsProviderAwarePayload() throws Exception {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.defaultModel").value("llama3:8b"))
                .andExpect(jsonPath("$.models[0]").value("llama3:8b"))
                .andExpect(jsonPath("$.models[1]").value("mistral:7b"));
    }

    @Test
    void ollamaDiscoveryErrorsMapToBadGateway() throws Exception {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ModelController(new ErrorAvailableModelsService()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();

        mockMvc.perform(get("/api/models"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("ollama unavailable"));
    }

    @Test
    void bedrockDiscoveryErrorsMapToBadGateway() throws Exception {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ModelController(new BedrockErrorAvailableModelsService()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();

        mockMvc.perform(get("/api/models"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("bedrock unavailable"));
    }

    private static final class FakeAvailableModelsService extends AvailableModelsService {
        private FakeAvailableModelsService() {
            super(null, null, null, null, null);
        }

        @Override
        public AvailableModelsResponse getAvailableModels() {
            return new AvailableModelsResponse("ollama", "llama3:8b", List.of("llama3:8b", "mistral:7b"));
        }
    }

    private static final class ErrorAvailableModelsService extends AvailableModelsService {
        private ErrorAvailableModelsService() {
            super(null, null, null, null, null);
        }

        @Override
        public AvailableModelsResponse getAvailableModels() {
            throw new OllamaClientException("ollama unavailable");
        }
    }

    private static final class BedrockErrorAvailableModelsService extends AvailableModelsService {
        private BedrockErrorAvailableModelsService() {
            super(null, null, null, null, null);
        }

        @Override
        public AvailableModelsResponse getAvailableModels() {
            throw new ModelDiscoveryException("bedrock unavailable");
        }
    }
}
