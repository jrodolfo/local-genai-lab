package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.BedrockCatalogClient;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AvailableModelsService {

    private final AppModelProperties appModelProperties;
    private final OllamaProperties ollamaProperties;
    private final BedrockProperties bedrockProperties;
    private final OllamaClient ollamaClient;
    private final BedrockCatalogClient bedrockCatalogClient;

    public AvailableModelsService(
            AppModelProperties appModelProperties,
            OllamaProperties ollamaProperties,
            BedrockProperties bedrockProperties,
            OllamaClient ollamaClient,
            @Nullable BedrockCatalogClient bedrockCatalogClient
    ) {
        this.appModelProperties = appModelProperties;
        this.ollamaProperties = ollamaProperties;
        this.bedrockProperties = bedrockProperties;
        this.ollamaClient = ollamaClient;
        this.bedrockCatalogClient = bedrockCatalogClient;
    }

    public AvailableModelsResponse getAvailableModels() {
        String provider = normalizeProvider(appModelProperties.provider());
        if ("bedrock".equals(provider)) {
            List<String> models = resolveBedrockModels();
            String modelId = resolveDefaultBedrockModel(models);
            return new AvailableModelsResponse(
                    "bedrock",
                    modelId,
                    models
            );
        }

        List<String> models = List.copyOf(new LinkedHashSet<>(ollamaClient.listModels()));
        return new AvailableModelsResponse(
                "ollama",
                normalizeModel(ollamaProperties.defaultModel()),
                models
        );
    }

    private List<String> resolveBedrockModels() {
        String configuredModelId = normalizeModel(bedrockProperties.modelId());
        LinkedHashSet<String> models = new LinkedHashSet<>();
        try {
            if (bedrockCatalogClient != null) {
                models.addAll(bedrockCatalogClient.listInferenceProfiles());
            }
        } catch (ModelDiscoveryException ex) {
            if (configuredModelId != null) {
                return List.of(configuredModelId);
            }
            throw ex;
        }
        if (configuredModelId != null) {
            models.add(configuredModelId);
        }
        return List.copyOf(new ArrayList<>(models));
    }

    private String resolveDefaultBedrockModel(List<String> models) {
        String configuredModelId = normalizeModel(bedrockProperties.modelId());
        if (configuredModelId != null && models.contains(configuredModelId)) {
            return configuredModelId;
        }
        if (!models.isEmpty()) {
            return models.getFirst();
        }
        return configuredModelId;
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "ollama";
        }
        return provider.trim().toLowerCase();
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        return model.trim();
    }
}
