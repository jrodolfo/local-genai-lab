package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AvailableModelsService {

    private final AppModelProperties appModelProperties;
    private final OllamaProperties ollamaProperties;
    private final BedrockProperties bedrockProperties;
    private final OllamaClient ollamaClient;

    public AvailableModelsService(
            AppModelProperties appModelProperties,
            OllamaProperties ollamaProperties,
            BedrockProperties bedrockProperties,
            OllamaClient ollamaClient
    ) {
        this.appModelProperties = appModelProperties;
        this.ollamaProperties = ollamaProperties;
        this.bedrockProperties = bedrockProperties;
        this.ollamaClient = ollamaClient;
    }

    public AvailableModelsResponse getAvailableModels() {
        String provider = normalizeProvider(appModelProperties.provider());
        if ("bedrock".equals(provider)) {
            String modelId = normalizeModel(bedrockProperties.modelId());
            return new AvailableModelsResponse(
                    "bedrock",
                    modelId,
                    modelId == null ? List.of() : List.of(modelId)
            );
        }

        List<String> models = List.copyOf(new LinkedHashSet<>(ollamaClient.listModels()));
        return new AvailableModelsResponse(
                "ollama",
                normalizeModel(ollamaProperties.defaultModel()),
                models
        );
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
