package net.jrodolfo.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.ModelProviderException;
import net.jrodolfo.llm.client.OllamaClientException;
import net.jrodolfo.llm.dto.ChatRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatStreamMetadata;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.service.ChatOrchestratorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatOrchestratorService chatOrchestratorService;
    private final ChatModelProvider chatModelProvider;
    private final ObjectMapper objectMapper;

    public ChatController(
            ChatOrchestratorService chatOrchestratorService,
            ChatModelProvider chatModelProvider,
            ObjectMapper objectMapper
    ) {
        this.chatOrchestratorService = chatOrchestratorService;
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatOrchestratorService.chat(request.message(), request.model(), request.sessionId());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                ChatOrchestratorService.PreparedChat preparedChat = chatOrchestratorService.prepareChat(
                        request.message(),
                        request.model(),
                        request.sessionId()
                );

                sendMetadata(emitter, new ChatStreamMetadata(
                        preparedChat.immediateResponse() != null ? preparedChat.immediateResponse().sessionId() : preparedChat.session().sessionId(),
                        preparedChat.toolMetadata(),
                        preparedChat.pendingTool(),
                        preparedChat.immediateResponse() != null ? preparedChat.immediateResponse().metadata() : null
                ));

                if (preparedChat.immediateResponse() != null) {
                    sendData(emitter, preparedChat.immediateResponse().response());
                    sendData(emitter, "[DONE]");
                    emitter.complete();
                    return;
                }

                StringBuilder responseBuffer = new StringBuilder();
                var providerMetadata = chatModelProvider.streamChat(preparedChat.prompt(), preparedChat.model(), token -> {
                    responseBuffer.append(token);
                    sendData(emitter, token);
                });
                chatOrchestratorService.completePreparedChat(preparedChat, responseBuffer.toString());
                sendMetadata(emitter, new ChatStreamMetadata(
                        preparedChat.session().sessionId(),
                        preparedChat.toolMetadata(),
                        preparedChat.pendingTool(),
                        providerMetadata
                ));
                sendData(emitter, "[DONE]");
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    @ExceptionHandler(OllamaClientException.class)
    public ResponseEntity<Map<String, String>> handleOllamaError(OllamaClientException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ModelProviderException.class)
    public ResponseEntity<Map<String, String>> handleModelProviderError(ModelProviderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    private void sendData(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().data(token));
        } catch (IOException ex) {
            throw new OllamaClientException("Failed to stream token to client.", ex);
        }
    }

    private void sendMetadata(SseEmitter emitter, ChatStreamMetadata metadata) {
        try {
            emitter.send(SseEmitter.event()
                    .name("metadata")
                    .data(objectMapper.writeValueAsString(metadata)));
        } catch (IOException ex) {
            throw new OllamaClientException("Failed to stream metadata to client.", ex);
        }
    }
}
