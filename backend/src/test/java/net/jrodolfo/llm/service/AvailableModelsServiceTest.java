package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.BedrockCatalogClient;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AvailableModelsServiceTest {

    @Test
    void bedrockUsesDiscoveredInferenceProfiles() {
        AvailableModelsService service = new AvailableModelsService(
                new AppModelProperties("bedrock"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", "us.amazon.nova-pro-v1:0"),
                new FakeOllamaClient(List.of()),
                () -> List.of("us.amazon.nova-pro-v1:0", "us.amazon.nova-lite-v1:0")
        );

        AvailableModelsResponse response = service.getAvailableModels();

        assertEquals("bedrock", response.provider());
        assertEquals("us.amazon.nova-pro-v1:0", response.defaultModel());
        assertEquals(List.of("us.amazon.nova-pro-v1:0", "us.amazon.nova-lite-v1:0"), response.models());
    }

    @Test
    void bedrockFallsBackToConfiguredModelWhenDiscoveryFails() {
        AvailableModelsService service = new AvailableModelsService(
                new AppModelProperties("bedrock"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", "us.amazon.nova-pro-v1:0"),
                new FakeOllamaClient(List.of()),
                () -> { throw new ModelDiscoveryException("bedrock unavailable"); }
        );

        AvailableModelsResponse response = service.getAvailableModels();

        assertEquals("bedrock", response.provider());
        assertEquals("us.amazon.nova-pro-v1:0", response.defaultModel());
        assertEquals(List.of("us.amazon.nova-pro-v1:0"), response.models());
    }

    @Test
    void bedrockDiscoveryFailureWithoutConfiguredModelPropagates() {
        AvailableModelsService service = new AvailableModelsService(
                new AppModelProperties("bedrock"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", null),
                new FakeOllamaClient(List.of()),
                () -> { throw new ModelDiscoveryException("bedrock unavailable"); }
        );

        ModelDiscoveryException exception = assertThrows(ModelDiscoveryException.class, service::getAvailableModels);

        assertEquals("bedrock unavailable", exception.getMessage());
    }

    @Test
    void ollamaUsesInstalledModels() {
        AvailableModelsService service = new AvailableModelsService(
                new AppModelProperties("ollama"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", "us.amazon.nova-pro-v1:0"),
                new FakeOllamaClient(List.of("llama3:8b", "mistral:7b")),
                null
        );

        AvailableModelsResponse response = service.getAvailableModels();

        assertEquals("ollama", response.provider());
        assertEquals("llama3:8b", response.defaultModel());
        assertEquals(List.of("llama3:8b", "mistral:7b"), response.models());
    }

    private static final class FakeOllamaClient extends OllamaClient {
        private final List<String> models;

        private FakeOllamaClient(List<String> models) {
            super(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60));
            this.models = models;
        }

        @Override
        public List<String> listModels() {
            return models;
        }
    }
}
