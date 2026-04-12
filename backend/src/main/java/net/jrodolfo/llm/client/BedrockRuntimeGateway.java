package net.jrodolfo.llm.client;

import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

public interface BedrockRuntimeGateway {

    ModelProviderReply converse(String prompt, String modelId);

    CompletableFuture<net.jrodolfo.llm.dto.ModelProviderMetadata> converseStream(String prompt, String modelId, Consumer<String> chunkConsumer);
}
