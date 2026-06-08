package net.jrodolfo.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClientException;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
import net.jrodolfo.llm.dto.ProviderStatusResponse;
import net.jrodolfo.llm.service.AvailableModelsService;
import net.jrodolfo.llm.service.InvalidProviderException;
import net.jrodolfo.llm.service.ProviderStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for retrieving available models and provider status.
 */
@RestController
@RequestMapping("/api/models")
@Tag(name = "models", description = "Available model options for the active provider.")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final AvailableModelsService availableModelsService;
    private final ProviderStatusService providerStatusService;

    /**
     * Constructs a new ModelController with the specified services.
     *
     * @param availableModelsService the service for listing available models.
     * @param providerStatusService  the service for checking provider status.
     */
    public ModelController(AvailableModelsService availableModelsService, ProviderStatusService providerStatusService) {
        this.availableModelsService = availableModelsService;
        this.providerStatusService = providerStatusService;
    }

    /**
     * Lists available models for a given provider.
     *
     * @param provider optional provider override.
     * @return the available models response.
     */
    @GetMapping
    @Operation(summary = "List available models", description = "Returns provider-aware model options the frontend can safely offer. Ollama returns installed local models; Bedrock returns discovered inference profiles when available and otherwise falls back to the configured model id.")
    public AvailableModelsResponse listAvailableModels(
            @Parameter(description = "Optional provider override. Falls back to the configured backend default when omitted.")
            @RequestParam(required = false) String provider
    ) {
        return availableModelsService.getAvailableModels(provider);
    }

    /**
     * Retrieves the status of a given provider.
     *
     * @param provider optional provider override.
     * @return the provider status response.
     */
    @GetMapping("/status")
    @Operation(summary = "Get provider status", description = "Returns a compact readiness and troubleshooting summary for the selected provider.")
    public ProviderStatusResponse getProviderStatus(
            @Parameter(description = "Optional provider override. Falls back to the configured backend default when omitted.")
            @RequestParam(required = false) String provider
    ) {
        return providerStatusService.getProviderStatus(provider);
    }

    /**
     * Exception handler for OllamaClientException.
     *
     * @param ex the exception.
     * @return a ResponseEntity with error details.
     */
    @ExceptionHandler(OllamaClientException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleOllamaClientException(OllamaClientException ex) {
        log.warn("Ollama model discovery failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    /**
     * Exception handler for ModelDiscoveryException.
     *
     * @param ex the exception.
     * @return a ResponseEntity with error details.
     */
    @ExceptionHandler(ModelDiscoveryException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleModelDiscoveryException(ModelDiscoveryException ex) {
        log.warn("Model discovery failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    /**
     * Exception handler for InvalidProviderException.
     *
     * @param ex the exception.
     * @return a ResponseEntity with error details.
     */
    @ExceptionHandler(InvalidProviderException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleInvalidProviderException(InvalidProviderException ex) {
        log.debug("Invalid provider requested: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
