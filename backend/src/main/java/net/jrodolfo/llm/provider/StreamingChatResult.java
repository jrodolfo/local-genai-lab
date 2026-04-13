package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.dto.ModelProviderMetadata;

import java.util.concurrent.CompletableFuture;

public record StreamingChatResult(
        CompletableFuture<ModelProviderMetadata> completion,
        Runnable cancel
) {

    public StreamingChatResult {
        cancel = cancel != null ? cancel : () -> { };
    }

    public void cancelStream() {
        cancel.run();
    }
}
