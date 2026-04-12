package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.dto.ModelProviderMetadata;

import java.util.concurrent.CompletableFuture;

public record StreamingChatResult(
        CompletableFuture<ModelProviderMetadata> completion
) {
}
