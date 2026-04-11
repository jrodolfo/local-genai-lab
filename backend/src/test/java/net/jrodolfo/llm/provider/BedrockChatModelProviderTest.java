package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.client.BedrockRuntimeGateway;
import net.jrodolfo.llm.client.ModelProviderException;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import org.junit.jupiter.api.Test;

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
    void streamingIsNotImplementedYet() {
        BedrockChatModelProvider provider = new BedrockChatModelProvider(
                new FakeBedrockRuntimeGateway(),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0")
        );

        assertThrows(ModelProviderException.class, () -> provider.streamChat("hello", null, token -> {
        }));
    }

    private static final class FakeBedrockRuntimeGateway implements BedrockRuntimeGateway {
        private String lastPrompt;
        private String lastModelId;

        @Override
        public String converse(String prompt, String modelId) {
            this.lastPrompt = prompt;
            this.lastModelId = modelId;
            return "bedrock response";
        }
    }
}
