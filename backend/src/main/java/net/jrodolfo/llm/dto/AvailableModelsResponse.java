package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Available model options for the active provider.")
public record AvailableModelsResponse(
        @Schema(description = "Active model provider.", example = "ollama")
        String provider,
        @Schema(description = "Configured default model for the active provider, when applicable.", example = "llama3:8b")
        String defaultModel,
        @Schema(description = "Safe model options the UI can offer for the active provider.")
        List<String> models
) {
}
