package net.jrodolfo.llm.dto;

/**
 * Backend timing metadata for a RAG answer.
 *
 * @param retrievalDurationMs duration spent retrieving source chunks
 * @param providerDurationMs  duration spent generating the provider answer
 * @param totalDurationMs     total backend duration for the RAG request
 */
public record RagTimingMetadata(
        Long retrievalDurationMs,
        Long providerDurationMs,
        Long totalDurationMs
) {
}
