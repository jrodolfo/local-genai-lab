package net.jrodolfo.llm.client;

import net.jrodolfo.llm.provider.ProviderPromptMessage;

import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

public interface BedrockRuntimeGateway {

    ModelProviderReply converse(String prompt, String modelId);

    ModelProviderReply converse(List<ProviderPromptMessage> messages, String modelId);

    CompletableFuture<net.jrodolfo.llm.dto.ModelProviderMetadata> converseStream(String prompt, String modelId, Consumer<String> chunkConsumer);

    CompletableFuture<net.jrodolfo.llm.dto.ModelProviderMetadata> converseStream(
            List<ProviderPromptMessage> messages,
            String modelId,
            Consumer<String> chunkConsumer
    );
}
