package net.jrodolfo.llm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.BedrockRuntimeGateway;
import net.jrodolfo.llm.client.ModelProviderReply;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.provider.BedrockChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.OllamaChatModelProvider;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelProviderConfigTest {

    private final ModelProviderConfig modelProviderConfig = new ModelProviderConfig();

    @Test
    void createsOllamaProviderByDefault() {
        ChatModelProvider provider = modelProviderConfig.chatModelProvider(
                new AppModelProperties("ollama"),
                new OllamaClient(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 10)),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new FakeBedrockRuntimeGateway()
        );

        assertInstanceOf(OllamaChatModelProvider.class, provider);
    }

    @Test
    void createsBedrockProviderWhenConfigured() {
        ChatModelProvider provider = modelProviderConfig.chatModelProvider(
                new AppModelProperties("bedrock"),
                new OllamaClient(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 10)),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new FakeBedrockRuntimeGateway()
        );

        assertInstanceOf(BedrockChatModelProvider.class, provider);
    }

    @Test
    void rejectsUnsupportedProviderNames() {
        assertThrows(IllegalStateException.class, () -> modelProviderConfig.chatModelProvider(
                new AppModelProperties("unsupported"),
                new OllamaClient(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 10)),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new FakeBedrockRuntimeGateway()
        ));
    }

    private static final class FakeBedrockRuntimeGateway implements BedrockRuntimeGateway {

        @Override
        public ModelProviderReply converse(String prompt, String modelId) {
            return new ModelProviderReply("ok", new ModelProviderMetadata("bedrock", modelId, null, null, null, null, null, null, null, null));
        }

        @Override
        public CompletableFuture<ModelProviderMetadata> converseStream(String prompt, String modelId, java.util.function.Consumer<String> chunkConsumer) {
            return CompletableFuture.completedFuture(
                    new ModelProviderMetadata("bedrock", modelId, null, null, null, null, null, null, null, null)
            );
        }
    }
}
