package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.client.BedrockRuntimeGateway;
import net.jrodolfo.llm.client.ModelProviderException;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedrockChatModelProviderTest {

    @Test
    void usesConfiguredDefaultModelWhenRequestModelIsBlank() {
        FakeBedrockRuntimeGateway gateway = new FakeBedrockRuntimeGateway();
        BedrockChatModelProvider provider = new BedrockChatModelProvider(
                gateway,
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0")
        );

        ChatResponse response = provider.chat("hello from bedrock", "   ", null, "session-1", null);

        assertEquals("amazon.nova-lite-v1:0", response.model());
        assertEquals("bedrock response", response.response());
        assertEquals("hello from bedrock", gateway.lastPrompt);
        assertEquals("amazon.nova-lite-v1:0", gateway.lastModelId);
    }

    @Test
    void throwsWhenNoBedrockModelIsConfigured() {
        BedrockChatModelProvider provider = new BedrockChatModelProvider(
                new FakeBedrockRuntimeGateway(),
                new BedrockProperties("us-east-1", " ")
        );

        assertThrows(ModelProviderException.class, () -> provider.resolveModel(" "));
    }

    @Test
    void streamChatUsesConfiguredDefaultModelWhenRequestModelIsBlank() {
        FakeBedrockRuntimeGateway gateway = new FakeBedrockRuntimeGateway();
        BedrockChatModelProvider provider = new BedrockChatModelProvider(
                gateway,
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0")
        );

        List<String> chunks = new ArrayList<>();
        provider.streamChat("hello", " ", chunks::add);

        assertEquals("hello", gateway.lastStreamPrompt);
        assertEquals("amazon.nova-lite-v1:0", gateway.lastStreamModelId);
        assertEquals(List.of("bedrock", " stream"), chunks);
    }

    private static final class FakeBedrockRuntimeGateway implements BedrockRuntimeGateway {
        private String lastPrompt;
        private String lastModelId;
        private String lastStreamPrompt;
        private String lastStreamModelId;

        @Override
        public String converse(String prompt, String modelId) {
            this.lastPrompt = prompt;
            this.lastModelId = modelId;
            return "bedrock response";
        }

        @Override
        public void converseStream(String prompt, String modelId, java.util.function.Consumer<String> chunkConsumer) {
            this.lastStreamPrompt = prompt;
            this.lastStreamModelId = modelId;
            chunkConsumer.accept("bedrock");
            chunkConsumer.accept(" stream");
        }
    }
}
