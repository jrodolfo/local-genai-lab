package net.jrodolfo.llm.rag.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request DTO for comparing one question across multiple RAG retrieval targets.
 *
 * @param question The question to run against each retrieval target.
 * @param provider The preferred LLM provider.
 * @param model The preferred model name.
 * @param retrievalTargets Optional target list. Defaults to all supported targets when omitted.
 */
public record RagComparisonRequest(
        @NotBlank(message = "question is required")
        String question,
        String provider,
        String model,
        List<String> retrievalTargets
) {
}
