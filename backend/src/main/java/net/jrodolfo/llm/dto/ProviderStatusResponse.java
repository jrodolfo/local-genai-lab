package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Compact provider readiness and troubleshooting status for the chat UI.")
public record ProviderStatusResponse(
        @Schema(description = "Provider whose status is being reported.", example = "ollama")
        String provider,
        @Schema(description = "Normalized status for the selected provider.", example = "ready")
        String status,
        @Schema(description = "Short user-facing explanation of the current provider state.")
        String message,
        @Schema(description = "ISO-8601 timestamp of the last provider status refresh.", example = "2026-04-19T20:15:00Z")
        String refreshedAt,
        @Schema(description = "Configured candidate models considered for the selected provider when applicable.")
        List<String> configuredModels,
        @Schema(description = "Models currently validated as usable for the selected provider when applicable.")
        List<String> usableModels,
        @Schema(description = "Configured models that were rejected during provider validation when applicable.")
        List<String> rejectedModels
) {
    public ProviderStatusResponse(String provider, String status, String message) {
        this(provider, status, message, null, List.of(), List.of(), List.of());
    }

    public ProviderStatusResponse(String provider, String status, String message, String refreshedAt) {
        this(provider, status, message, refreshedAt, List.of(), List.of(), List.of());
    }
}
