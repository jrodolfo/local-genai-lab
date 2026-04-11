package net.jrodolfo.llm.client;

public interface BedrockRuntimeGateway {

    String converse(String prompt, String modelId);
}
