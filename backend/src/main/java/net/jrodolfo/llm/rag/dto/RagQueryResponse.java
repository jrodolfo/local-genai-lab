package net.jrodolfo.llm.rag.dto;

import net.jrodolfo.llm.dto.ModelProviderMetadata;

import java.util.List;

public record RagQueryResponse(
        String answer,
        String provider,
        String model,
        List<RagSourceChunkResponse> sources,
        ModelProviderMetadata metadata
) {
}
