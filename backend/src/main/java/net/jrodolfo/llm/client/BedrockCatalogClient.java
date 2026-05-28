package net.jrodolfo.llm.client;

import java.util.List;

/**
 * Interface for discovering available models and inference profiles in Amazon Bedrock.
 */
public interface BedrockCatalogClient {

    /**
     * Lists the available inference profile IDs in Amazon Bedrock.
     *
     * @return a list of inference profile IDs
     */
    List<String> listInferenceProfiles();
}
