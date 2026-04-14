package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Runtime metadata returned by a chat model provider.")
public record ModelProviderMetadata(
        String provider,
        String modelId,
        String stopReason,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Long durationMs,
        Long providerLatencyMs,
        Long backendDurationMs,
        Long uiWaitMs
) {
}
