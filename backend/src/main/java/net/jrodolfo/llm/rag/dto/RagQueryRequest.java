package net.jrodolfo.llm.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record RagQueryRequest(
        @NotBlank(message = "question is required")
        String question,
        String provider,
        String model
) {
}
