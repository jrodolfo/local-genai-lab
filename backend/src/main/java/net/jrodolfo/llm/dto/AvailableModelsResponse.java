package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Available model options for the active provider.
 */
@Schema(description = "Available model options for the active provider.")
public record AvailableModelsResponse(
        @Schema(description = "Provider whose models are included in this payload.", example = "ollama")
        String provider,
        @Schema(description = "Configured backend default provider.", example = "ollama")
        String defaultProvider,
        @Schema(description = "Provider options the UI can offer for runtime switching.")
        List<String> providers,
        @Schema(description = "Configured default model for the active provider, when applicable.", example = "llama3:8b")
        String defaultModel,
        @Schema(description = "Safe model options the UI can offer for the active provider.")
        List<String> models
) {
}
