package net.jrodolfo.llm.rag.dto;

import java.util.List;

/**
 * Response DTO for a RAG retrieval comparison run.
 *
 * @param question The question compared across retrieval targets.
 * @param results Per-target comparison results in request order.
 */
public record RagComparisonResponse(
        String question,
        List<RagComparisonTargetResponse> results
) {
}
