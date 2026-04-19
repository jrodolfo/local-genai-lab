package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.BedrockCatalogClient;
import net.jrodolfo.llm.client.HuggingFaceClient;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AvailableModelsServiceTest {

    @Test
    void bedrockUsesDiscoveredInferenceProfiles() {
        AvailableModelsService service = new AvailableModelsService(
                new ChatModelProviderRegistry(new AppModelProperties("ollama"), java.util.Map.of(
                        "ollama", new FakeProvider(),
                        "bedrock", new FakeProvider()
                )),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", "us.amazon.nova-pro-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeOllamaClient(List.of()),
                () -> List.of("us.amazon.nova-pro-v1:0", "us.amazon.nova-lite-v1:0"),
                null
        );

        AvailableModelsResponse response = service.getAvailableModels("bedrock");

        assertEquals("bedrock", response.provider());
        assertEquals("ollama", response.defaultProvider());
        assertEquals(List.of("bedrock", "ollama"), response.providers());
        assertEquals("us.amazon.nova-pro-v1:0", response.defaultModel());
        assertEquals(List.of("us.amazon.nova-pro-v1:0", "us.amazon.nova-lite-v1:0"), response.models());
    }

    @Test
    void bedrockFallsBackToConfiguredModelWhenDiscoveryFails() {
        AvailableModelsService service = new AvailableModelsService(
                new ChatModelProviderRegistry(new AppModelProperties("ollama"), java.util.Map.of(
                        "ollama", new FakeProvider(),
                        "bedrock", new FakeProvider()
                )),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", "us.amazon.nova-pro-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeOllamaClient(List.of()),
                () -> { throw new ModelDiscoveryException("bedrock unavailable"); },
                null
        );

        AvailableModelsResponse response = service.getAvailableModels("bedrock");

        assertEquals("bedrock", response.provider());
        assertEquals("us.amazon.nova-pro-v1:0", response.defaultModel());
        assertEquals(List.of("us.amazon.nova-pro-v1:0"), response.models());
    }

    @Test
    void bedrockDiscoveryFailureWithoutConfiguredModelPropagates() {
        AvailableModelsService service = new AvailableModelsService(
                new ChatModelProviderRegistry(new AppModelProperties("ollama"), java.util.Map.of(
                        "ollama", new FakeProvider(),
                        "bedrock", new FakeProvider()
                )),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", null),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeOllamaClient(List.of()),
                () -> { throw new ModelDiscoveryException("bedrock unavailable"); },
                null
        );

        ModelDiscoveryException exception = assertThrows(ModelDiscoveryException.class, () -> service.getAvailableModels("bedrock"));

        assertEquals("bedrock unavailable", exception.getMessage());
    }

    @Test
    void ollamaUsesInstalledModels() {
        AvailableModelsService service = new AvailableModelsService(
                new ChatModelProviderRegistry(new AppModelProperties("ollama"), java.util.Map.of(
                        "ollama", new FakeProvider(),
                        "bedrock", new FakeProvider()
                )),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", "us.amazon.nova-pro-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeOllamaClient(List.of("llama3:8b", "mistral:7b")),
                null,
                null
        );

        AvailableModelsResponse response = service.getAvailableModels("ollama");

        assertEquals("ollama", response.provider());
        assertEquals("llama3:8b", response.defaultModel());
        assertEquals(List.of("llama3:8b", "mistral:7b"), response.models());
    }

    @Test
    void huggingFaceReturnsOnlyUsableConfiguredModels() {
        AvailableModelsService service = new AvailableModelsService(
                new ChatModelProviderRegistry(new AppModelProperties("huggingface"), java.util.Map.of(
                        "ollama", new FakeProvider(),
                        "huggingface", new FakeProvider()
                )),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", "us.amazon.nova-pro-v1:0"),
                new HuggingFaceProperties(
                        "https://router.huggingface.co/v1/chat/completions",
                        "token",
                        "meta-llama/Llama-3.1-8B-Instruct",
                        List.of("Qwen/Qwen2.5-72B-Instruct", "meta-llama/Llama-3.1-8B-Instruct"),
                        10,
                        60
                ),
                new FakeOllamaClient(List.of("llama3:8b")),
                null,
                new FakeHuggingFaceClient(List.of("meta-llama/Llama-3.1-8B-Instruct"))
        );

        AvailableModelsResponse response = service.getAvailableModels("huggingface");

        assertEquals("huggingface", response.provider());
        assertEquals("huggingface", response.defaultProvider());
        assertEquals("meta-llama/Llama-3.1-8B-Instruct", response.defaultModel());
        assertEquals(List.of("meta-llama/Llama-3.1-8B-Instruct"), response.models());
    }

    @Test
    void huggingFaceFallsBackToFirstUsableModelWhenConfiguredDefaultIsUnavailable() {
        AvailableModelsService service = new AvailableModelsService(
                new ChatModelProviderRegistry(new AppModelProperties("huggingface"), java.util.Map.of(
                        "ollama", new FakeProvider(),
                        "huggingface", new FakeProvider()
                )),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", "us.amazon.nova-pro-v1:0"),
                new HuggingFaceProperties(
                        "https://router.huggingface.co/v1/chat/completions",
                        "token",
                        "meta-llama/Llama-3.1-8B-Instruct",
                        List.of("Qwen/Qwen2.5-72B-Instruct", "meta-llama/Llama-3.1-8B-Instruct"),
                        10,
                        60
                ),
                new FakeOllamaClient(List.of("llama3:8b")),
                null,
                new FakeHuggingFaceClient(List.of("Qwen/Qwen2.5-72B-Instruct"))
        );

        AvailableModelsResponse response = service.getAvailableModels("huggingface");

        assertEquals("Qwen/Qwen2.5-72B-Instruct", response.defaultModel());
        assertEquals(List.of("Qwen/Qwen2.5-72B-Instruct"), response.models());
    }

    @Test
    void providersListIncludesOnlyConfiguredProvidersForCurrentProcess() {
        AvailableModelsService service = new AvailableModelsService(
                new ChatModelProviderRegistry(new AppModelProperties("huggingface"), java.util.Map.of(
                        "ollama", new FakeProvider(),
                        "bedrock", new FakeProvider(),
                        "huggingface", new FakeProvider()
                )),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-2", null),
                new HuggingFaceProperties(
                        "https://router.huggingface.co/v1/chat/completions",
                        "token",
                        "meta-llama/Llama-3.1-8B-Instruct",
                        List.of("meta-llama/Llama-3.1-8B-Instruct"),
                        10,
                        60
                ),
                new FakeOllamaClient(List.of("llama3:8b")),
                null,
                new FakeHuggingFaceClient(List.of("meta-llama/Llama-3.1-8B-Instruct"))
        );

        AvailableModelsResponse response = service.getAvailableModels("huggingface");

        assertEquals(List.of("huggingface", "ollama"), response.providers());
    }

    private static final class FakeProvider implements net.jrodolfo.llm.provider.ChatModelProvider {
        @Override
        public net.jrodolfo.llm.dto.ChatResponse chat(net.jrodolfo.llm.provider.ProviderPrompt prompt, String model, net.jrodolfo.llm.dto.ChatToolMetadata toolMetadata, java.util.Map<String, Object> toolResult, String sessionId, net.jrodolfo.llm.dto.PendingToolCallResponse pendingTool) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.jrodolfo.llm.provider.StreamingChatResult streamChat(net.jrodolfo.llm.provider.ProviderPrompt prompt, String model, java.util.function.Consumer<String> tokenConsumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String resolveModel(String model) {
            return model;
        }
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

    private static final class FakeHuggingFaceClient extends HuggingFaceClient {
        private final List<String> usableModels;

        private FakeHuggingFaceClient(List<String> usableModels) {
            super(new ObjectMapper(), new HuggingFaceProperties(
                    "https://router.huggingface.co/v1/chat/completions",
                    "token",
                    "meta-llama/Llama-3.1-8B-Instruct",
                    usableModels,
                    10,
                    60
            ));
            this.usableModels = usableModels;
        }

        @Override
        public List<String> discoverUsableModels(List<String> candidateModels) {
            return candidateModels.stream().filter(usableModels::contains).toList();
        }
    }
}
