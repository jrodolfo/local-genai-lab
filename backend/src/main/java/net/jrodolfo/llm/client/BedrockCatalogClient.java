package net.jrodolfo.llm.client;

import java.util.List;

public interface BedrockCatalogClient {

    List<String> listInferenceProfiles();
}
