package net.jrodolfo.llm.client;

import java.util.List;

/**
 * Interface for discovering available models and inference profiles in Amazon Bedrock.
 */
public interface BedrockCatalogClient {

    List<String> listInferenceProfiles();
}
