package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Runtime metadata returned by a chat model provider and displayed in technical details.
 *
 * @param provider          resolved provider id
 * @param modelId           resolved provider model id
 * @param stopReason        provider-specific completion/stop reason, when available
 * @param inputTokens       provider-reported input token count, when available
 * @param outputTokens      provider-reported output token count, when available
 * @param totalTokens       provider-reported total token count, when available
 * @param durationMs        provider call duration measured by the provider adapter
 * @param providerLatencyMs provider-reported latency, when distinct from adapter duration
 * @param backendDurationMs total backend request duration added by the controller
 * @param uiWaitMs          browser-observed wait time added by the frontend
 * @param phaseTimingsMs    optional backend phase timing breakdown in milliseconds
 */
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
        Long uiWaitMs,
        Map<String, Long> phaseTimingsMs
) {
    public ModelProviderMetadata(
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
        this(
                provider,
                modelId,
                stopReason,
                inputTokens,
                outputTokens,
                totalTokens,
                durationMs,
                providerLatencyMs,
                backendDurationMs,
                uiWaitMs,
                null
        );
    }
}
