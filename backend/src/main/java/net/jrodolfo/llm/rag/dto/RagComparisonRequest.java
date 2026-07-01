package net.jrodolfo.llm.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request body for comparing one RAG question across retrieval targets without session side effects.")
public record RagComparisonRequest(
        @Schema(description = "Question to run against each retrieval target.", example = "How are sessions persisted?")
        @NotBlank(message = "question is required")
        String question,
        @Schema(description = "Optional provider override. Falls back to the configured backend default when omitted.", example = "ollama")
        String provider,
        @Schema(description = "Optional model override. Falls back to the selected provider default when omitted.", example = "llama3:8b")
        String model,
        @Schema(description = "Optional retrieval targets. Supported values: lexical, vector:in-memory, vector:qdrant.")
        List<String> retrievalTargets
) {
}
