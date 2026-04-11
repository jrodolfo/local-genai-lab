package net.jrodolfo.llm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.provider.BedrockChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.OllamaChatModelProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelProviderConfigTest {

    private final ModelProviderConfig modelProviderConfig = new ModelProviderConfig();

    @Test
    void createsOllamaProviderByDefault() {
        ChatModelProvider provider = modelProviderConfig.chatModelProvider(
                new AppModelProperties("ollama"),
                new OllamaClient(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 10)),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0")
        );

        assertInstanceOf(OllamaChatModelProvider.class, provider);
    }

    @Test
    void createsBedrockProviderWhenConfigured() {
        ChatModelProvider provider = modelProviderConfig.chatModelProvider(
                new AppModelProperties("bedrock"),
                new OllamaClient(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 10)),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0")
        );

        assertInstanceOf(BedrockChatModelProvider.class, provider);
    }

    @Test
    void rejectsUnsupportedProviderNames() {
        assertThrows(IllegalStateException.class, () -> modelProviderConfig.chatModelProvider(
                new AppModelProperties("unsupported"),
                new OllamaClient(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 10)),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0")
        ));
    }
}
