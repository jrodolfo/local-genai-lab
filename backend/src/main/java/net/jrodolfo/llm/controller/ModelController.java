package net.jrodolfo.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClientException;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
import net.jrodolfo.llm.service.AvailableModelsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/models")
@Tag(name = "models", description = "Available model options for the active provider.")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final AvailableModelsService availableModelsService;

    public ModelController(AvailableModelsService availableModelsService) {
        this.availableModelsService = availableModelsService;
    }

    @GetMapping
    @Operation(summary = "List available models", description = "Returns provider-aware model options the frontend can safely offer. Ollama returns installed local models; Bedrock returns discovered inference profiles when available and otherwise falls back to the configured model id.")
    public AvailableModelsResponse listAvailableModels() {
        return availableModelsService.getAvailableModels();
    }

    @ExceptionHandler(OllamaClientException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleOllamaClientException(OllamaClientException ex) {
        log.error("Ollama model discovery failed", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ModelDiscoveryException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleModelDiscoveryException(ModelDiscoveryException ex) {
        log.error("Model discovery failed", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }
}
