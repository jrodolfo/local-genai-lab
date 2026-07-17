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
import org.springframework.mock.web.MockHttpServletResponse;
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

        ResponseEntity<ChatResponse> response = controller.chat(
                new ChatRequest("hello", "bedrock", "us.amazon.nova-pro-v1:0", "session-9"),
                "req-123"
        );

        assertEquals("hello", orchestrator.lastMessage);
        assertEquals("bedrock", orchestrator.lastProvider);
        assertEquals("us.amazon.nova-pro-v1:0", orchestrator.lastModel);
        assertEquals("session-9", orchestrator.lastSessionId);
        assertEquals("req-123", orchestrator.lastRequestId);
        assertEquals("req-123", response.getHeaders().getFirst("X-Request-Id"));
        assertEquals("hello response", response.getBody().response());
        assertEquals("bedrock", response.getBody().metadata().provider());
        assertEquals("us.amazon.nova-pro-v1:0", response.getBody().metadata().modelId());
        assertTrue(response.getBody().metadata().backendDurationMs() >= 0);
    }

    @Test
    void chatGeneratesRequestIdWhenHeaderMissing() {
        TestOrchestrator orchestrator = new TestOrchestrator();
        ChatController controller = new ChatController(orchestrator, new ObjectMapper(), Runnable::run);

        ResponseEntity<ChatResponse> response = controller.chat(
                new ChatRequest("hello", "bedrock", "us.amazon.nova-pro-v1:0", "session-9"),
                null
        );

        assertTrue(orchestrator.lastRequestId != null && !orchestrator.lastRequestId.isBlank());
        assertEquals(orchestrator.lastRequestId, response.getHeaders().getFirst("X-Request-Id"));
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

    @Test
    void streamSetsRequestIdHeaderAndPassesItToOrchestrator() {
        TestOrchestrator orchestrator = new TestOrchestrator();
        ChatController controller = new ChatController(orchestrator, new ObjectMapper(), Runnable::run);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.stream(new ChatRequest("hello", "ollama", "llama3:8b", null), "req-stream-7", response);

        assertEquals("req-stream-7", response.getHeader("X-Request-Id"));
        assertEquals("req-stream-7", orchestrator.lastRequestId);
    }

    @Test
    void streamUsesImmediateResponseWithoutStartingProviderStream() {
        TestOrchestrator orchestrator = new TestOrchestrator();
        orchestrator.immediateResponse = new ChatResponse(
                "S3 CloudWatch report completed for bucket `jrodolfo.net`.\n\nResults: success_count=5, failure_count=0, skipped_count=0.",
                "llama3:8b",
                new ChatToolMetadata(true, "s3_cloudwatch_report", "success", "S3 report completed."),
                Map.of("type", "s3_report_summary", "bucket", "jrodolfo.net"),
                "session-s3",
                null,
                null
        );
        ChatController controller = new ChatController(orchestrator, new ObjectMapper(), Runnable::run);
        CallbackEmitter emitter = new CallbackEmitter();

        controller.stream(new ChatRequest("run an s3 report", "ollama", "llama3:8b", null), emitter);

        assertEquals(0, orchestrator.completePreparedChatCalls);
        assertEquals(0, orchestrator.streamCalls);
        assertTrue(emitter.completed);
        assertFalse(emitter.completedWithError);
    }

    @Test
    void streamUsesMaterializedImmediateResponseWithoutStartingProviderStream() {
        TestOrchestrator orchestrator = new TestOrchestrator();
        orchestrator.materializedImmediateResponse = new ChatResponse(
                "S3 CloudWatch report completed for bucket `jrodolfo.net`.\n\nResults: success_count=5, failure_count=0, skipped_count=0.",
                "llama3:8b",
                new ChatToolMetadata(true, "s3_cloudwatch_report", "success", "S3 report completed."),
                Map.of("type", "s3_report_summary", "bucket", "jrodolfo.net"),
                "session-s3-materialized",
                null,
                null
        );
        ChatController controller = new ChatController(orchestrator, new ObjectMapper(), Runnable::run);
        CallbackEmitter emitter = new CallbackEmitter();

        controller.stream(new ChatRequest("run an s3 report", "ollama", "llama3:8b", null), emitter);

        assertEquals(1, orchestrator.materializeImmediateResponseCalls);
        assertEquals(0, orchestrator.completePreparedChatCalls);
        assertEquals(0, orchestrator.streamCalls);
        assertTrue(emitter.completed);
        assertFalse(emitter.completedWithError);
    }

    private static final class TestOrchestrator extends ChatOrchestratorService {
        private int streamCalls;
        private ChatModelProvider provider = new SynchronousStreamingProvider(() -> streamCalls++);
        private final PreparedChat preparedChat;
        private ChatResponse immediateResponse;
        private ChatResponse materializedImmediateResponse;
        private int materializeImmediateResponseCalls;
        private int completePreparedChatCalls;
        private String lastMessage;
        private String lastProvider;
        private String lastModel;
        private String lastSessionId;
        private String lastRequestId;

        private TestOrchestrator() {
            super(new ChatModelProviderRegistry(new net.jrodolfo.llm.config.AppModelProperties("ollama"), java.util.Map.of("ollama", new SynchronousStreamingProvider(() -> { }))), null, null, null, null, null, new AppStorageProperties("data/sessions", "agents/reports"));
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
        public ChatResponse chat(String message, String provider, String model, String sessionId, String requestId) {
            this.lastRequestId = requestId;
            return chat(message, provider, model, sessionId);
        }

        @Override
        public PreparedChat prepareChat(String message, String provider, String model, String sessionId) {
            if (immediateResponse != null) {
                return new PreparedChat(
                        null,
                        null,
                        immediateResponse.model(),
                        immediateResponse.tool(),
                        immediateResponse.toolResult(),
                        immediateResponse.pendingTool(),
                        null,
                        immediateResponse
                );
            }
            return new PreparedChat(this.provider, preparedChat.prompt(), preparedChat.model(), preparedChat.toolMetadata(), preparedChat.toolResult(), preparedChat.pendingTool(), preparedChat.session(), preparedChat.immediateResponse());
        }

        @Override
        public PreparedChat prepareChat(String message, String provider, String model, String sessionId, String requestId) {
            this.lastRequestId = requestId;
            return prepareChat(message, provider, model, sessionId);
        }

        @Override
        public PreparedChat prepareChat(
                String message,
                String provider,
                String model,
                String sessionId,
                String requestId,
                ToolPhaseListener toolPhaseListener
        ) {
            this.lastRequestId = requestId;
            return prepareChat(message, provider, model, sessionId);
        }

        @Override
        public ChatResponse materializeImmediateResponse(PreparedChat preparedChat) {
            materializeImmediateResponseCalls++;
            if (immediateResponse != null) {
                return immediateResponse;
            }
            return materializedImmediateResponse;
        }

        @Override
        public ChatSession completePreparedChat(PreparedChat preparedChat, String assistantResponse, ModelProviderMetadata providerMetadata) {
            completePreparedChatCalls++;
            return preparedChat.session();
        }

        @Override
        public ChatSession completePreparedChat(
                PreparedChat preparedChat,
                String assistantResponse,
                ModelProviderMetadata providerMetadata,
                String requestId
        ) {
            completePreparedChatCalls++;
            this.lastRequestId = requestId;
            return preparedChat.session();
        }
    }

    private static final class SynchronousStreamingProvider implements ChatModelProvider {
        private final Runnable onStream;

        private SynchronousStreamingProvider(Runnable onStream) {
            this.onStream = onStream;
        }

        @Override
        public ChatResponse chat(ProviderPrompt message, String model, ChatToolMetadata toolMetadata, Map<String, Object> toolResult, String sessionId, PendingToolCallResponse pendingTool) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamingChatResult streamChat(ProviderPrompt message, String model, Consumer<String> tokenConsumer) {
            onStream.run();
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
