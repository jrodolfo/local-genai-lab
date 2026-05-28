package net.jrodolfo.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import net.jrodolfo.llm.client.ModelProviderException;
import net.jrodolfo.llm.client.OllamaClientException;
import net.jrodolfo.llm.dto.ChatRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatStreamEvent;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.service.ChatOrchestratorService;
import net.jrodolfo.llm.service.InvalidProviderException;
import net.jrodolfo.llm.service.InvalidSessionIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP entrypoint for normal and streaming chat interactions.
 *
 * <p>The controller exposes a small transport contract and delegates tool routing, persistence,
 * and provider selection to the orchestration and provider layers.
 */
@RestController
@RequestMapping("/api/chat")
@Tag(name = "chat", description = "Normal and streaming chat endpoints.")
public class ChatController {

    private static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatOrchestratorService chatOrchestratorService;
    private final ObjectMapper objectMapper;
    private final Executor chatStreamingExecutor;

    /**
     * Constructs a new ChatController with the specified orchestrator, mapper, and executor.
     *
     * @param chatOrchestratorService the service that orchestrates chat interactions.
     * @param objectMapper            the Jackson mapper for JSON serialization/deserialization.
     * @param chatStreamingExecutor   the executor for managing streaming tasks.
     */
    public ChatController(
            ChatOrchestratorService chatOrchestratorService,
            ObjectMapper objectMapper,
            @Qualifier("chatStreamingExecutor") Executor chatStreamingExecutor
    ) {
        this.chatOrchestratorService = chatOrchestratorService;
        this.objectMapper = objectMapper;
        this.chatStreamingExecutor = chatStreamingExecutor;
    }

    /**
     * Handles a non-streaming chat request.
     *
     * @param request         the chat request containing message and provider details.
     * @param requestIdHeader an optional request identifier from headers.
     * @return a ResponseEntity containing the ChatResponse.
     */
    @PostMapping
    @Operation(summary = "Run a non-streaming chat request", description = "Routes the request through the active provider, optionally using local MCP-backed tools before the model call.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chat response returned successfully."),
            @ApiResponse(responseCode = "400", description = "Invalid request body."),
            @ApiResponse(responseCode = "502", description = "Provider or MCP integration failed.")
    })
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestIdHeader
    ) {
        String requestId = resolveRequestId(requestIdHeader);
        long startedAt = System.nanoTime();
        log.info(
                "requestId={} chat_request_start provider={} model={} sessionId={} streaming=false",
                requestId,
                request.provider(),
                request.model(),
                request.sessionId()
        );
        ChatResponse response = chatOrchestratorService.chat(
                request.message(),
                request.provider(),
                request.model(),
                request.sessionId(),
                requestId
        );
        long backendDurationMs = elapsedMillis(startedAt);
        log.info(
                "requestId={} chat_request_complete provider={} model={} sessionId={} streaming=false backendDurationMs={}",
                requestId,
                request.provider(),
                response.model(),
                response.sessionId(),
                backendDurationMs
        );
        return ResponseEntity.ok()
                .header(REQUEST_ID_HEADER, requestId)
                .body(new ChatResponse(
                        response.response(),
                        response.model(),
                        response.tool(),
                        response.toolResult(),
                        response.sessionId(),
                        response.pendingTool(),
                        withBackendDuration(response.metadata(), backendDurationMs)
                ));
    }

    /**
     * Handles a streaming chat request using Server-Sent Events (SSE).
     *
     * @param request         the chat request containing message and provider details.
     * @param requestIdHeader an optional request identifier from headers.
     * @param response        the HTTP servlet response.
     * @return an SseEmitter for streaming events.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Run a streaming chat request",
            description = "Streams Server-Sent Events using a typed JSON envelope. The stream emits `chat` events with additive tool-phase events, `type=start`, zero or more `type=delta` chunks, and a final `type=complete` event."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "SSE stream started successfully.",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            examples = @ExampleObject(value = "event: chat\\ndata: {\"type\":\"start\",\"sessionId\":\"session-123\"}\\n\\nevent: chat\\ndata: {\"type\":\"delta\",\"text\":\"Hello\"}\\n\\nevent: chat\\ndata: {\"type\":\"complete\",\"sessionId\":\"session-123\"}\\n\\n"))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request body."),
            @ApiResponse(responseCode = "502", description = "Provider or MCP integration failed.")
    })
    public SseEmitter stream(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestIdHeader,
            HttpServletResponse response
    ) {
        String requestId = resolveRequestId(requestIdHeader);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        stream(request, emitter, requestId);
        return emitter;
    }

    /**
     * Shared streaming workflow used by the HTTP endpoint and controller tests.
     *
     * <p>The method emits a typed start event, then either sends an immediate response for
     * clarification/failure paths or streams provider deltas until completion. Aborted SSE streams
     * are treated as client disconnects rather than provider failures.
     */
    void stream(ChatRequest request, SseEmitter emitter) {
        stream(request, emitter, resolveRequestId(null));
    }

    /**
     * Internal method to initiate a streaming chat workflow.
     *
     * @param request   the chat request.
     * @param emitter   the SseEmitter to send events to.
     * @param requestId the unique request identifier.
     */
    void stream(ChatRequest request, SseEmitter emitter, String requestId) {
        long startedAt = System.nanoTime();
        log.info(
                "requestId={} chat_request_start provider={} model={} sessionId={} streaming=true",
                requestId,
                request.provider(),
                request.model(),
                request.sessionId()
        );
        AtomicBoolean streamClosed = new AtomicBoolean(false);
        AtomicBoolean streamAborted = new AtomicBoolean(false);
        AtomicReference<CompletableFuture<?>> taskReference = new AtomicReference<>();
        AtomicReference<net.jrodolfo.llm.provider.StreamingChatResult> streamingResultReference = new AtomicReference<>();

        Runnable cancelStream = () -> {
            streamClosed.set(true);
            streamAborted.set(true);
            net.jrodolfo.llm.provider.StreamingChatResult streamingResult = streamingResultReference.get();
            if (streamingResult != null) {
                streamingResult.cancelStream();
            }
            CompletableFuture<?> task = taskReference.get();
            if (task != null) {
                task.cancel(true);
            }
        };

        emitter.onCompletion(cancelStream);
        emitter.onTimeout(cancelStream);
        emitter.onError(error -> cancelStream.run());

        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                ChatOrchestratorService.PreparedChat preparedChat = chatOrchestratorService.prepareChat(
                        request.message(),
                        request.provider(),
                        request.model(),
                        request.sessionId(),
                        requestId,
                        (phaseType, toolName) -> {
                            if (!sendEvent(emitter, streamClosed, ChatStreamEvent.phase(phaseType, toolName))) {
                                throw new StreamAbortedException();
                            }
                        }
                );

                if (preparedChat.immediateResponse() == null
                        && preparedChat.toolMetadata() != null
                        && preparedChat.toolMetadata().used()
                        && "success".equals(preparedChat.toolMetadata().status())) {
                    if (!sendEvent(emitter, streamClosed, ChatStreamEvent.phase(
                            "answer-generation-started",
                            preparedChat.toolMetadata().name()
                    ))) {
                        streamAborted.set(true);
                        return;
                    }
                }

                if (!sendEvent(emitter, streamClosed, ChatStreamEvent.start(
                        preparedChat.immediateResponse() != null ? preparedChat.immediateResponse().sessionId() : preparedChat.session().sessionId(),
                        preparedChat.toolMetadata(),
                        preparedChat.toolResult(),
                        preparedChat.pendingTool(),
                        preparedChat.immediateResponse() != null ? preparedChat.immediateResponse().metadata() : null
                ))) {
                    streamAborted.set(true);
                    return;
                }

                if (preparedChat.immediateResponse() != null) {
                    long backendDurationMs = elapsedMillis(startedAt);
                    if (!sendEvent(emitter, streamClosed, ChatStreamEvent.delta(preparedChat.immediateResponse().response()))) {
                        streamAborted.set(true);
                        return;
                    }
                    if (!sendEvent(emitter, streamClosed, ChatStreamEvent.complete(
                            preparedChat.immediateResponse().sessionId(),
                            preparedChat.immediateResponse().tool(),
                            preparedChat.immediateResponse().toolResult(),
                            preparedChat.immediateResponse().pendingTool(),
                            withBackendDuration(preparedChat.immediateResponse().metadata(), backendDurationMs)
                    ))) {
                        streamAborted.set(true);
                        return;
                    }
                    log.info(
                            "requestId={} chat_request_complete provider={} model={} sessionId={} streaming=true backendDurationMs={} immediateResponse=true",
                            requestId,
                            request.provider(),
                            preparedChat.immediateResponse().model(),
                            preparedChat.immediateResponse().sessionId(),
                            backendDurationMs
                    );
                    completeEmitter(emitter, streamClosed);
                    return;
                }

                StringBuilder responseBuffer = new StringBuilder();
                var streamingResult = preparedChat.provider().streamChat(preparedChat.prompt(), preparedChat.model(), token -> {
                    if (streamClosed.get()) {
                        throw new StreamAbortedException();
                    }
                    responseBuffer.append(token);
                    if (!sendEvent(emitter, streamClosed, ChatStreamEvent.delta(token))) {
                        throw new StreamAbortedException();
                    }
                });
                streamingResultReference.set(streamingResult);
                if (streamClosed.get()) {
                    streamAborted.set(true);
                    streamingResult.cancelStream();
                    return;
                }
                var providerMetadata = streamingResult.completion().join();
                if (streamClosed.get()) {
                    streamAborted.set(true);
                    return;
                }
                chatOrchestratorService.completePreparedChat(preparedChat, responseBuffer.toString(), providerMetadata, requestId);
                long backendDurationMs = elapsedMillis(startedAt);
                if (!sendEvent(emitter, streamClosed, ChatStreamEvent.complete(
                        preparedChat.session().sessionId(),
                        preparedChat.toolMetadata(),
                        preparedChat.toolResult(),
                        preparedChat.pendingTool(),
                        withBackendDuration(providerMetadata, backendDurationMs)
                ))) {
                    streamAborted.set(true);
                    return;
                }
                log.info(
                        "requestId={} chat_request_complete provider={} model={} sessionId={} streaming=true backendDurationMs={} immediateResponse=false",
                        requestId,
                        request.provider(),
                        preparedChat.model(),
                        preparedChat.session().sessionId(),
                        backendDurationMs
                );
                completeEmitter(emitter, streamClosed);
            } catch (StreamAbortedException ex) {
                streamAborted.set(true);
            } catch (Exception ex) {
                Throwable cause = unwrapCompletionException(ex);
                if (!streamClosed.get()) {
                    log.warn(
                            "requestId={} chat_request_failed provider={} model={} sessionId={} streaming=true message={}",
                            requestId,
                            request.provider(),
                            request.model(),
                            request.sessionId(),
                            cause.getMessage()
                    );
                    emitter.completeWithError(cause);
                }
            } finally {
                if (streamAborted.get()) {
                    log.info(
                            "requestId={} chat_request_aborted provider={} model={} sessionId={} streaming=true",
                            requestId,
                            request.provider(),
                            request.model(),
                            request.sessionId()
                    );
                    completeEmitter(emitter, streamClosed);
                }
            }
        }, chatStreamingExecutor);
        taskReference.set(task);
    }

    /**
     * Exception handler for OllamaClientException.
     *
     * @param ex the exception.
     * @return a ResponseEntity with error details.
     */
    @ExceptionHandler(OllamaClientException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleOllamaError(OllamaClientException ex) {
        log.warn("Ollama request failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Exception handler for ModelProviderException.
     *
     * @param ex the exception.
     * @return a ResponseEntity with error details.
     */
    @ExceptionHandler(ModelProviderException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleModelProviderError(ModelProviderException ex) {
        log.warn("Model provider request failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Exception handler for InvalidSessionIdException.
     *
     * @param ex the exception.
     * @return a ResponseEntity with error details.
     */
    @ExceptionHandler(InvalidSessionIdException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleInvalidSessionId(InvalidSessionIdException ex) {
        log.warn("Invalid session id", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Exception handler for InvalidProviderException.
     *
     * @param ex the exception.
     * @return a ResponseEntity with error details.
     */
    @ExceptionHandler(InvalidProviderException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleInvalidProvider(InvalidProviderException ex) {
        log.warn("Invalid provider", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    private String resolveRequestId(String requestIdHeader) {
        if (requestIdHeader == null || requestIdHeader.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestIdHeader.trim();
    }

    private boolean sendEvent(SseEmitter emitter, AtomicBoolean streamClosed, ChatStreamEvent event) {
        if (streamClosed.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name("chat")
                    .data(objectMapper.writeValueAsString(event)));
            return true;
        } catch (IOException ex) {
            streamClosed.set(true);
            return false;
        } catch (IllegalStateException ex) {
            streamClosed.set(true);
            return false;
        }
    }

    private void completeEmitter(SseEmitter emitter, AtomicBoolean streamClosed) {
        if (streamClosed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    /**
     * Preserves provider-reported metadata while adding backend-side elapsed time for the full
     * request lifecycle.
     */
    private ModelProviderMetadata withBackendDuration(ModelProviderMetadata metadata, long backendDurationMs) {
        if (metadata == null) {
            return null;
        }
        return new ModelProviderMetadata(
                metadata.provider(),
                metadata.modelId(),
                metadata.stopReason(),
                metadata.inputTokens(),
                metadata.outputTokens(),
                metadata.totalTokens(),
                metadata.durationMs(),
                metadata.providerLatencyMs(),
                backendDurationMs,
                metadata.uiWaitMs()
        );
    }

    private long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static final class StreamAbortedException extends RuntimeException {
    }
}
