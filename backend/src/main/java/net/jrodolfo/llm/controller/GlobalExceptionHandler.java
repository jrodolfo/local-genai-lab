package net.jrodolfo.llm.controller;

import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.ModelProviderException;
import net.jrodolfo.llm.client.OllamaClientException;
import net.jrodolfo.llm.service.ArtifactAccessException;
import net.jrodolfo.llm.service.ChatSessionImportException;
import net.jrodolfo.llm.service.ChatSessionNotFoundException;
import net.jrodolfo.llm.service.InvalidProviderException;
import net.jrodolfo.llm.service.InvalidSessionIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OllamaClientException.class)
    public ResponseEntity<Map<String, String>> handleOllamaError(OllamaClientException ex) {
        log.warn("Ollama request failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ModelProviderException.class)
    public ResponseEntity<Map<String, String>> handleModelProviderError(ModelProviderException ex) {
        log.warn("Model provider request failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidSessionIdException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSessionId(InvalidSessionIdException ex) {
        log.warn("Invalid session id: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidProviderException.class)
    public ResponseEntity<Map<String, String>> handleInvalidProvider(InvalidProviderException ex) {
        log.warn("Invalid provider: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ModelDiscoveryException.class)
    public ResponseEntity<Map<String, String>> handleModelDiscoveryException(ModelDiscoveryException ex) {
        log.warn("Model discovery failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(McpClientException.class)
    public ResponseEntity<Map<String, String>> handleMcpError(McpClientException ex) {
        HttpStatus status = "MCP integration is disabled.".equals(ex.getMessage())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ChatSessionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleChatSessionNotFound(ChatSessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ChatSessionImportException.class)
    public ResponseEntity<Map<String, String>> handleChatSessionImport(ChatSessionImportException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ArtifactAccessException.class)
    public ResponseEntity<Map<String, String>> handleArtifactAccess(ArtifactAccessException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
