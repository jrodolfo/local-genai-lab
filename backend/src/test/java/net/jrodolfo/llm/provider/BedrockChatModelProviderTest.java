package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.client.BedrockRuntimeGateway;
import net.jrodolfo.llm.client.ModelProviderReply;
import net.jrodolfo.llm.client.ModelProviderException;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedrockChatModelProviderTest {

    @Test
    void usesConfiguredDefaultModelWhenRequestModelIsBlank() {
        FakeBedrockRuntimeGateway gateway = new FakeBedrockRuntimeGateway();
        BedrockChatModelProvider provider = new BedrockChatModelProvider(
                gateway,
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0")
        );

        ChatResponse response = provider.chat(ProviderPrompt.forPrompt("hello from bedrock"), "   ", null, null, "session-1", null);

        assertEquals("amazon.nova-lite-v1:0", response.model());
        assertEquals("bedrock response", response.response());
        assertEquals("bedrock", response.metadata().provider());
        assertEquals("amazon.nova-lite-v1:0", response.metadata().modelId());
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
        StreamingChatResult result = provider.streamChat(ProviderPrompt.forPrompt("hello"), " ", chunks::add);
        ModelProviderMetadata metadata = result.completion().join();

        assertEquals("hello", gateway.lastStreamPrompt);
        assertEquals("amazon.nova-lite-v1:0", gateway.lastStreamModelId);
        assertEquals(List.of("bedrock", " stream"), chunks);
        assertEquals("bedrock", metadata.provider());
        assertEquals("amazon.nova-lite-v1:0", metadata.modelId());
    }

    @Test
    void streamChatCanEmitChunksBeforeCompletionMetadataIsReady() {
        DelayedBedrockRuntimeGateway gateway = new DelayedBedrockRuntimeGateway();
        BedrockChatModelProvider provider = new BedrockChatModelProvider(
                gateway,
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0")
        );

        List<String> chunks = new ArrayList<>();
        StreamingChatResult result = provider.streamChat(ProviderPrompt.forPrompt("hello"), " ", chunks::add);

        assertEquals(List.of("bedrock"), chunks);
        assertFalse(result.completion().isDone());

        gateway.finish();

        assertEquals("bedrock", result.completion().join().provider());
    }

    @Test
    void usesStructuredMessagesWhenProviderPromptContainsHistory() {
        FakeBedrockRuntimeGateway gateway = new FakeBedrockRuntimeGateway();
        BedrockChatModelProvider provider = new BedrockChatModelProvider(
                gateway,
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0")
        );

        provider.chat(
                ProviderPrompt.forMessages(
                        List.of(
                                new ProviderPromptMessage("system", "You are helpful."),
                                new ProviderPromptMessage("user", "hello"),
                                new ProviderPromptMessage("assistant", "hi"),
                                new ProviderPromptMessage("user", "follow up")
                        ),
                        "fallback"
                ),
                " ",
                null,
                null,
                "session-1",
                null
        );

        assertEquals(List.of(
                new ProviderPromptMessage("system", "You are helpful."),
                new ProviderPromptMessage("user", "hello"),
                new ProviderPromptMessage("assistant", "hi"),
                new ProviderPromptMessage("user", "follow up")
        ), gateway.lastMessages);
    }

    private static final class FakeBedrockRuntimeGateway implements BedrockRuntimeGateway {
        private String lastPrompt;
        private String lastModelId;
        private String lastStreamPrompt;
        private String lastStreamModelId;
        private List<ProviderPromptMessage> lastMessages = List.of();

        @Override
        public ModelProviderReply converse(String prompt, String modelId) {
            this.lastPrompt = prompt;
            this.lastModelId = modelId;
            return new ModelProviderReply(
                    "bedrock response",
                    new ModelProviderMetadata("bedrock", modelId, "end_turn", 10, 20, 30, 400L, 390L, null, null)
            );
        }

        @Override
        public ModelProviderReply converse(List<ProviderPromptMessage> messages, String modelId) {
            this.lastMessages = List.copyOf(messages);
            this.lastModelId = modelId;
            return new ModelProviderReply(
                    "bedrock response",
                    new ModelProviderMetadata("bedrock", modelId, "end_turn", 10, 20, 30, 400L, 390L, null, null)
            );
        }

        @Override
        public CompletableFuture<ModelProviderMetadata> converseStream(String prompt, String modelId, java.util.function.Consumer<String> chunkConsumer) {
            this.lastStreamPrompt = prompt;
            this.lastStreamModelId = modelId;
            chunkConsumer.accept("bedrock");
            chunkConsumer.accept(" stream");
            return CompletableFuture.completedFuture(
                    new ModelProviderMetadata("bedrock", modelId, "end_turn", 1, 2, 3, 4L, 5L, null, null)
            );
        }

        @Override
        public CompletableFuture<ModelProviderMetadata> converseStream(
                List<ProviderPromptMessage> messages,
                String modelId,
                java.util.function.Consumer<String> chunkConsumer
        ) {
            this.lastMessages = List.copyOf(messages);
            this.lastStreamModelId = modelId;
            chunkConsumer.accept("bedrock");
            chunkConsumer.accept(" stream");
            return CompletableFuture.completedFuture(
                    new ModelProviderMetadata("bedrock", modelId, "end_turn", 1, 2, 3, 4L, 5L, null, null)
            );
        }
    }

    private static final class DelayedBedrockRuntimeGateway implements BedrockRuntimeGateway {
        private final CompletableFuture<ModelProviderMetadata> completion = new CompletableFuture<>();

        @Override
        public ModelProviderReply converse(String prompt, String modelId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelProviderReply converse(List<ProviderPromptMessage> messages, String modelId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<ModelProviderMetadata> converseStream(String prompt, String modelId, java.util.function.Consumer<String> chunkConsumer) {
            chunkConsumer.accept("bedrock");
            return completion;
        }

        @Override
        public CompletableFuture<ModelProviderMetadata> converseStream(
                List<ProviderPromptMessage> messages,
                String modelId,
                java.util.function.Consumer<String> chunkConsumer
        ) {
            chunkConsumer.accept("bedrock");
            return completion;
        }

        void finish() {
            completion.complete(new ModelProviderMetadata("bedrock", "amazon.nova-lite-v1:0", "end_turn", 1, 2, 3, 4L, 5L, null, null));
        }
    }
}
