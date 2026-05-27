package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.dto.ModelProviderMetadata;

import java.util.concurrent.CompletableFuture;

/**
 * Result of a streaming chat request.
 *
 * @param completion a future that completes when the stream is finished, providing the final metadata
 * @param cancel a runnable to cancel the ongoing stream
 */
public record StreamingChatResult(
        CompletableFuture<ModelProviderMetadata> completion,
        Runnable cancel
) {

    public StreamingChatResult {
        cancel = cancel != null ? cancel : () -> { };
    }

    /**
     * Cancels the ongoing chat stream.
     */
    public void cancelStream() {
        cancel.run();
    }
}
