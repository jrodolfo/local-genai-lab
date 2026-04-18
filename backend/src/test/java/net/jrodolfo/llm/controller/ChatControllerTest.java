package net.jrodolfo.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.dto.ChatRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import net.jrodolfo.llm.provider.ProviderPrompt;
import net.jrodolfo.llm.provider.StreamingChatResult;
import net.jrodolfo.llm.service.ChatOrchestratorService;
import net.jrodolfo.llm.service.InvalidProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerTest {

    @Test
    void chatPassesRequestProviderFieldsToOrchestrator() {
        TestOrchestrator orchestrator = new TestOrchestrator();
        ChatController controller = new ChatController(orchestrator, new ObjectMapper(), Runnable::run);

        ChatResponse response = controller.chat(new ChatRequest("hello", "bedrock", "us.amazon.nova-pro-v1:0", "session-9"));

        assertEquals("hello", orchestrator.lastMessage);
        assertEquals("bedrock", orchestrator.lastProvider);
        assertEquals("us.amazon.nova-pro-v1:0", orchestrator.lastModel);
        assertEquals("session-9", orchestrator.lastSessionId);
        assertEquals("hello response", response.response());
        assertEquals("bedrock", response.metadata().provider());
        assertEquals("us.amazon.nova-pro-v1:0", response.metadata().modelId());
        assertTrue(response.metadata().backendDurationMs() >= 0);
    }

    @Test
    void invalidProviderIsReturnedAsBadRequest() {
        ChatController controller = new ChatController(new TestOrchestrator(), new ObjectMapper(), Runnable::run);

        ResponseEntity<Map<String, String>> response = controller.handleInvalidProvider(new InvalidProviderException("unsupported provider"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("unsupported provider", response.getBody().get("error"));
    }

    @Test
    void failedDeltaSendDoesNotPersistAbortedTurn() {
        TestOrchestrator orchestrator = new TestOrchestrator();
        ChatController controller = new ChatController(orchestrator, new ObjectMapper(), Runnable::run);
        FailingEmitter emitter = new FailingEmitter(2);

        controller.stream(new ChatRequest("hello", "ollama", "llama3:8b", null), emitter);

        assertEquals(0, orchestrator.completePreparedChatCalls);
        assertFalse(emitter.completedWithError);
    }

    @Test
    void completionCallbackCancelsActiveStreamAndSkipsPersistence() throws Exception {
        TestOrchestrator orchestrator = new TestOrchestrator();
        Executor executor = Executors.newSingleThreadExecutor();
        try {
            DelayedStreamingProvider provider = new DelayedStreamingProvider();
            orchestrator.provider = provider;
            ChatController controller = new ChatController(orchestrator, new ObjectMapper(), executor);
            CallbackEmitter emitter = new CallbackEmitter();

            controller.stream(new ChatRequest("hello", "ollama", "llama3:8b", null), emitter);
            assertTrue(provider.streamStarted.await(2, TimeUnit.SECONDS));

            emitter.fireCompletion();

            assertTrue(provider.cancelled.await(2, TimeUnit.SECONDS));
            assertEquals(0, orchestrator.completePreparedChatCalls);
            assertFalse(emitter.completedWithError);
        } finally {
            if (executor instanceof java.util.concurrent.ExecutorService executorService) {
                executorService.shutdownNow();
            }
        }
    }

    private static final class TestOrchestrator extends ChatOrchestratorService {
        private ChatModelProvider provider = new SynchronousStreamingProvider();
        private final PreparedChat preparedChat;
        private int completePreparedChatCalls;
        private String lastMessage;
        private String lastProvider;
        private String lastModel;
        private String lastSessionId;

        private TestOrchestrator() {
            super(new ChatModelProviderRegistry(new net.jrodolfo.llm.config.AppModelProperties("ollama"), java.util.Map.of("ollama", new SynchronousStreamingProvider())), null, null, null, null, null, new AppStorageProperties("data/sessions", "scripts/reports"));
            ChatSession session = ChatSession.create("session-1", "llama3:8b", Instant.parse("2026-04-12T00:00:00Z"));
            this.preparedChat = new PreparedChat(provider, ProviderPrompt.forPrompt("prompt"), "llama3:8b", null, null, null, session, null);
        }

        @Override
        public ChatResponse chat(String message, String provider, String model, String sessionId) {
            this.lastMessage = message;
            this.lastProvider = provider;
            this.lastModel = model;
            this.lastSessionId = sessionId;
            return new ChatResponse(
                    message + " response",
                    model,
                    null,
                    null,
                    sessionId,
                    null,
                    new ModelProviderMetadata(provider, model, null, null, null, null, null, null, null, null)
            );
        }

        @Override
        public PreparedChat prepareChat(String message, String provider, String model, String sessionId) {
            return new PreparedChat(this.provider, preparedChat.prompt(), preparedChat.model(), preparedChat.toolMetadata(), preparedChat.toolResult(), preparedChat.pendingTool(), preparedChat.session(), preparedChat.immediateResponse());
        }

        @Override
        public ChatSession completePreparedChat(PreparedChat preparedChat, String assistantResponse, ModelProviderMetadata providerMetadata) {
            completePreparedChatCalls++;
            return preparedChat.session();
        }
    }

    private static final class SynchronousStreamingProvider implements ChatModelProvider {
        @Override
        public ChatResponse chat(ProviderPrompt message, String model, ChatToolMetadata toolMetadata, Map<String, Object> toolResult, String sessionId, PendingToolCallResponse pendingTool) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamingChatResult streamChat(ProviderPrompt message, String model, Consumer<String> tokenConsumer) {
            tokenConsumer.accept("chunk-1");
            return new StreamingChatResult(
                    CompletableFuture.completedFuture(new ModelProviderMetadata("ollama", model, null, null, null, null, null, null, null, null)),
                    () -> { }
            );
        }

        @Override
        public String resolveModel(String model) {
            return model;
        }
    }

    private static final class DelayedStreamingProvider implements ChatModelProvider {
        private final CountDownLatch streamStarted = new CountDownLatch(1);
        private final CountDownLatch cancelled = new CountDownLatch(1);

        @Override
        public ChatResponse chat(ProviderPrompt message, String model, ChatToolMetadata toolMetadata, Map<String, Object> toolResult, String sessionId, PendingToolCallResponse pendingTool) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamingChatResult streamChat(ProviderPrompt message, String model, Consumer<String> tokenConsumer) {
            streamStarted.countDown();
            return new StreamingChatResult(new CompletableFuture<>(), cancelled::countDown);
        }

        @Override
        public String resolveModel(String model) {
            return model;
        }
    }

    private static class CallbackEmitter extends SseEmitter {
        private Runnable completionCallback = () -> { };
        private Consumer<Throwable> errorCallback = error -> { };
        private Runnable timeoutCallback = () -> { };
        boolean completed;
        boolean completedWithError;

        @Override
        public void onCompletion(Runnable callback) {
            this.completionCallback = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            this.errorCallback = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            this.timeoutCallback = callback;
        }

        @Override
        public void complete() {
            this.completed = true;
        }

        @Override
        public void completeWithError(Throwable ex) {
            this.completedWithError = true;
            this.errorCallback.accept(ex);
        }

        void fireCompletion() {
            completionCallback.run();
        }

        void fireTimeout() {
            timeoutCallback.run();
        }
    }

    private static final class FailingEmitter extends CallbackEmitter {
        private final int failureAtSend;
        private int sendCount;

        private FailingEmitter(int failureAtSend) {
            this.failureAtSend = failureAtSend;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendCount++;
            if (sendCount >= failureAtSend) {
                throw new IOException("simulated disconnect");
            }
        }
    }
}
