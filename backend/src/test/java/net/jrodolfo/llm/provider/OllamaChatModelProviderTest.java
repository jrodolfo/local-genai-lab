package net.jrodolfo.llm.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaChatModelProviderTest {

    @Test
    void chatIncludesDurationMetadataForGenerateRequests() {
        FakeOllamaClient client = new FakeOllamaClient();
        OllamaChatModelProvider provider = new OllamaChatModelProvider(client);

        ChatResponse response = provider.chat(ProviderPrompt.forPrompt("hello from ollama"), "llama3:8b", null, null, "session-1", null);

        assertEquals("ollama response", response.response());
        assertEquals("ollama", response.metadata().provider());
        assertEquals("llama3:8b", response.metadata().modelId());
        assertNotNull(response.metadata().durationMs());
        assertTrue(response.metadata().durationMs() >= 0);
    }

    @Test
    void streamChatIncludesDurationMetadata() {
        FakeOllamaClient client = new FakeOllamaClient();
        OllamaChatModelProvider provider = new OllamaChatModelProvider(client);
        List<String> chunks = new ArrayList<>();

        StreamingChatResult result = provider.streamChat(ProviderPrompt.forPrompt("hello"), "llama3:8b", chunks::add);
        ModelProviderMetadata metadata = result.completion().join();

        assertEquals(List.of("ollama", " stream"), chunks);
        assertEquals("ollama", metadata.provider());
        assertEquals("llama3:8b", metadata.modelId());
        assertNotNull(metadata.durationMs());
        assertTrue(metadata.durationMs() >= 0);
    }

    @Test
    void streamChatMeasuresBlockingStreamDuration() {
        SlowStreamingOllamaClient client = new SlowStreamingOllamaClient();
        OllamaChatModelProvider provider = new OllamaChatModelProvider(client);

        StreamingChatResult result = provider.streamChat(ProviderPrompt.forPrompt("hello"), "llama3:8b", chunk -> { });
        ModelProviderMetadata metadata = result.completion().join();

        assertNotNull(metadata.durationMs());
        assertTrue(metadata.durationMs() >= 25);
    }

    private static final class FakeOllamaClient extends OllamaClient {

        private FakeOllamaClient() {
            super(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60));
        }

        @Override
        public String generate(String prompt, String model) {
            return "ollama response";
        }

        @Override
        public void streamGenerate(String prompt, String model, java.util.function.Consumer<String> tokenConsumer) {
            tokenConsumer.accept("ollama");
            tokenConsumer.accept(" stream");
        }
    }

    private static final class SlowStreamingOllamaClient extends OllamaClient {

        private SlowStreamingOllamaClient() {
            super(new ObjectMapper(), new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60));
        }

        @Override
        public void streamGenerate(String prompt, String model, java.util.function.Consumer<String> tokenConsumer) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
            tokenConsumer.accept("done");
        }
    }
}
