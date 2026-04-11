package net.jrodolfo.llm.client;

import java.util.function.Consumer;

public interface BedrockRuntimeGateway {

    String converse(String prompt, String modelId);

    void converseStream(String prompt, String modelId, Consumer<String> chunkConsumer);
}
