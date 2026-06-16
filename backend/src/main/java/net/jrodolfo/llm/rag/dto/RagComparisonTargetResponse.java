package net.jrodolfo.llm.rag.dto;

import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.RagRetrievalMetadata;
import net.jrodolfo.llm.dto.RagTimingMetadata;

import java.util.List;

/**
 * Response DTO for one retrieval target in a RAG comparison run.
 *
 * @param retrievalTarget The target value, such as {@code lexical} or {@code vector:qdrant}.
 * @param success Whether this target produced an answer.
 * @param error User-facing error for this target when it failed.
 * @param answer Generated answer when the target succeeded.
 * @param provider Provider used to generate the answer.
 * @param model Model used to generate the answer.
 * @param sources Source chunks used for this target.
 * @param metadata Provider metadata for the generated answer.
 * @param ragRetrieval Retrieval metadata for this target.
 * @param ragTiming Backend timing metadata for this target.
 */
public record RagComparisonTargetResponse(
        String retrievalTarget,
        boolean success,
        String error,
        String answer,
        String provider,
        String model,
        List<RagSourceChunkResponse> sources,
        ModelProviderMetadata metadata,
        RagRetrievalMetadata ragRetrieval,
        RagTimingMetadata ragTiming
) {
}
