package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.BedrockCatalogClient;
import net.jrodolfo.llm.client.HuggingFaceClient;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.dto.AvailableModelsResponse;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Resolves model options that the frontend can safely present for the active provider.
 *
 * <p>Ollama returns installed local models. Bedrock prefers discovered inference profiles but
 * falls back to the configured model id so the UI remains usable when discovery fails.
 */
@Service
public class AvailableModelsService {

    private final ChatModelProviderRegistry chatModelProviderRegistry;
    private final OllamaProperties ollamaProperties;
    private final BedrockProperties bedrockProperties;
    private final HuggingFaceProperties huggingFaceProperties;
    private final OllamaClient ollamaClient;
    private final BedrockCatalogClient bedrockCatalogClient;
    private final HuggingFaceClient huggingFaceClient;

    public AvailableModelsService(
            ChatModelProviderRegistry chatModelProviderRegistry,
            OllamaProperties ollamaProperties,
            BedrockProperties bedrockProperties,
            HuggingFaceProperties huggingFaceProperties,
            OllamaClient ollamaClient,
            @Nullable BedrockCatalogClient bedrockCatalogClient,
            @Nullable HuggingFaceClient huggingFaceClient
    ) {
        this.chatModelProviderRegistry = chatModelProviderRegistry;
        this.ollamaProperties = ollamaProperties;
        this.bedrockProperties = bedrockProperties;
        this.huggingFaceProperties = huggingFaceProperties;
        this.ollamaClient = ollamaClient;
        this.bedrockCatalogClient = bedrockCatalogClient;
        this.huggingFaceClient = huggingFaceClient;
    }

    public AvailableModelsResponse getAvailableModels(String provider) {
        String resolvedProvider = chatModelProviderRegistry.resolveProviderName(provider);
        chatModelProviderRegistry.get(resolvedProvider);
        List<String> availableProviders = resolveAvailableProviders();
        if ("bedrock".equals(resolvedProvider)) {
            List<String> models = resolveBedrockModels();
            String modelId = resolveDefaultBedrockModel(models);
            return new AvailableModelsResponse(
                    "bedrock",
                    chatModelProviderRegistry.defaultProvider(),
                    availableProviders,
                    modelId,
                    models
            );
        }
        if ("huggingface".equals(resolvedProvider)) {
            List<String> models = resolveHuggingFaceModels();
            return new AvailableModelsResponse(
                    "huggingface",
                    chatModelProviderRegistry.defaultProvider(),
                    availableProviders,
                    resolveDefaultHuggingFaceModel(models),
                    models
            );
        }

        List<String> models = List.copyOf(new LinkedHashSet<>(ollamaClient.listModels()));
        return new AvailableModelsResponse(
                "ollama",
                chatModelProviderRegistry.defaultProvider(),
                availableProviders,
                normalizeModel(ollamaProperties.defaultModel()),
                models
        );
    }

    private List<String> resolveAvailableProviders() {
        return chatModelProviderRegistry.supportedProviders().stream()
                .filter(this::isProviderAvailable)
                .toList();
    }

    private boolean isProviderAvailable(String provider) {
        return switch (provider) {
            case "bedrock" -> normalizeModel(bedrockProperties.region()) != null
                    && normalizeModel(bedrockProperties.modelId()) != null;
            case "huggingface" -> normalizeModel(huggingFaceProperties.apiToken()) != null
                    && normalizeModel(huggingFaceProperties.baseUrl()) != null
                    && normalizeModel(huggingFaceProperties.defaultModel()) != null;
            case "ollama" -> true;
            default -> false;
        };
    }

    private List<String> resolveBedrockModels() {
        String configuredModelId = normalizeModel(bedrockProperties.modelId());
        LinkedHashSet<String> models = new LinkedHashSet<>();
        try {
            if (bedrockCatalogClient != null) {
                models.addAll(bedrockCatalogClient.listInferenceProfiles());
            }
        } catch (ModelDiscoveryException ex) {
            // Keep the selector functional even when the account cannot list inference profiles in
            // the current region or lacks permission to do so.
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

    private List<String> resolveHuggingFaceModels() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (huggingFaceProperties.models() != null) {
            for (String model : huggingFaceProperties.models()) {
                String normalized = normalizeModel(model);
                if (normalized != null) {
                    models.add(normalized);
                }
            }
        }
        String configuredModelId = normalizeModel(huggingFaceProperties.defaultModel());
        if (configuredModelId != null) {
            models.add(configuredModelId);
        }
        List<String> configuredCandidates = List.copyOf(new ArrayList<>(models));
        if (configuredCandidates.isEmpty() || huggingFaceClient == null) {
            return configuredCandidates;
        }
        return huggingFaceClient.discoverUsableModels(configuredCandidates);
    }

    private String resolveDefaultHuggingFaceModel(List<String> models) {
        String configuredModelId = normalizeModel(huggingFaceProperties.defaultModel());
        if (configuredModelId != null && models.contains(configuredModelId)) {
            return configuredModelId;
        }
        if (!models.isEmpty()) {
            return models.getFirst();
        }
        return configuredModelId;
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        return model.trim();
    }
}
