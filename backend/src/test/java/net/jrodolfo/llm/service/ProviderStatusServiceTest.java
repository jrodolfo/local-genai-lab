package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.HuggingFaceClient;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.client.OllamaClientException;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.dto.ProviderStatusResponse;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderStatusServiceTest {

    @Test
    void ollamaStatusIsReadyWhenDefaultModelIsInstalled() {
        ProviderStatusService service = new ProviderStatusService(
                registry("ollama"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeOllamaClient(List.of("llama3:8b")),
                new FakeHuggingFaceClient(List.of("meta-llama/Llama-3.1-8B-Instruct")),
                () -> true
        );

        ProviderStatusResponse response = service.getProviderStatus("ollama");

        assertEquals("ready", response.status());
    }

    @Test
    void ollamaStatusIsUnreachableWhenDiscoveryFails() {
        ProviderStatusService service = new ProviderStatusService(
                registry("ollama"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FailingOllamaClient(),
                new FakeHuggingFaceClient(List.of("meta-llama/Llama-3.1-8B-Instruct")),
                () -> true
        );

        ProviderStatusResponse response = service.getProviderStatus("ollama");

        assertEquals("unreachable", response.status());
    }

    @Test
    void bedrockStatusIsReadyWhenRegionAndModelAreConfigured() {
        ProviderStatusService service = new ProviderStatusService(
                registry("bedrock"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeOllamaClient(List.of("llama3:8b")),
                new FakeHuggingFaceClient(List.of("meta-llama/Llama-3.1-8B-Instruct")),
                () -> {
                    throw new IllegalStateException("missing creds");
                }
        );

        ProviderStatusResponse response = service.getProviderStatus("bedrock");

        assertEquals("ready", response.status());
    }

    @Test
    void huggingFaceStatusIsMisconfiguredWhenTokenIsMissing() {
        ProviderStatusService service = new ProviderStatusService(
                registry("huggingface"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeOllamaClient(List.of("llama3:8b")),
                new FakeHuggingFaceClient(List.of("meta-llama/Llama-3.1-8B-Instruct")),
                () -> true
        );

        ProviderStatusResponse response = service.getProviderStatus("huggingface");

        assertEquals("misconfigured", response.status());
    }

    @Test
    void huggingFaceStatusReportsModelMissingWhenNoConfiguredModelsAreUsable() {
        ProviderStatusService service = new ProviderStatusService(
                registry("huggingface"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeOllamaClient(List.of("llama3:8b")),
                new FakeHuggingFaceClient(List.of()),
                () -> true
        );

        ProviderStatusResponse response = service.getProviderStatus("huggingface");

        assertEquals("model_missing", response.status());
        assertEquals(List.of("meta-llama/Llama-3.1-8B-Instruct"), response.configuredModels());
        assertEquals(List.of(), response.usableModels());
        assertEquals(List.of("meta-llama/Llama-3.1-8B-Instruct"), response.rejectedModels());
    }

    @Test
    void huggingFaceStatusReportsUnreachableWhenDiscoveryFails() {
        ProviderStatusService service = new ProviderStatusService(
                registry("huggingface"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeOllamaClient(List.of("llama3:8b")),
                new FailingHuggingFaceClient(),
                () -> true
        );

        ProviderStatusResponse response = service.getProviderStatus("huggingface");

        assertEquals("unreachable", response.status());
    }

    private ChatModelProviderRegistry registry(String defaultProvider) {
        return new ChatModelProviderRegistry(
                new AppModelProperties(defaultProvider),
                Map.of(
                        "ollama", new FakeProvider(),
                        "bedrock", new FakeProvider(),
                        "huggingface", new FakeProvider()
                )
        );
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

    private static final class FailingOllamaClient extends OllamaClient {
        private FailingOllamaClient() {
            super(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60));
        }

        @Override
        public List<String> listModels() {
            throw new OllamaClientException("unreachable");
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

    private static final class FailingHuggingFaceClient extends HuggingFaceClient {
        private FailingHuggingFaceClient() {
            super(new ObjectMapper(), new HuggingFaceProperties(
                    "https://router.huggingface.co/v1/chat/completions",
                    "token",
                    "meta-llama/Llama-3.1-8B-Instruct",
                    List.of("meta-llama/Llama-3.1-8B-Instruct"),
                    10,
                    60
            ));
        }

        @Override
        public List<String> discoverUsableModels(List<String> candidateModels) {
            throw new net.jrodolfo.llm.client.ModelDiscoveryException("down");
        }
    }
}
