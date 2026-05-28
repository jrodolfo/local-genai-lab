package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.BedrockCatalogClient;
import net.jrodolfo.llm.client.HuggingFaceClient;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClient;
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
 * Resolves provider/model options that the frontend can safely present in the current backend
 * process.
 *
 * <p>The returned provider list is intentionally narrower than the provider registry:
 * only providers with enough configuration to be usable in this running process are surfaced to
 * the selector. Model discovery is then provider-specific:
 *
 * <ul>
 *   <li>Ollama returns installed local models.</li>
 *   <li>Bedrock prefers discovered inference profiles but falls back to the configured model id.</li>
 *   <li>Hugging Face starts from configured candidates and validates the usable subset.</li>
 * </ul>
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

    /**
     * Constructs a new AvailableModelsService.
     *
     * @param chatModelProviderRegistry the registry of chat model providers
     * @param ollamaProperties          properties for Ollama
     * @param bedrockProperties         properties for AWS Bedrock
     * @param huggingFaceProperties     properties for Hugging Face
     * @param ollamaClient              client for Ollama
     * @param bedrockCatalogClient      client for AWS Bedrock catalog
     * @param huggingFaceClient         client for Hugging Face
     */
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

    /**
     * Gets the available models for a specific provider.
     *
     * @param provider the name of the provider
     * @return the available models response
     */
    public AvailableModelsResponse getAvailableModels(String provider) {
        String resolvedProvider = chatModelProviderRegistry.resolveProviderName(provider);
        chatModelProviderRegistry.get(resolvedProvider);
        // The selector should only advertise providers that are configured in this backend process,
        // even if additional provider beans exist in the codebase.
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

    /**
     * Resolves the list of available providers based on configuration.
     *
     * @return a list of available provider names
     */
    private List<String> resolveAvailableProviders() {
        return chatModelProviderRegistry.supportedProviders().stream()
                .filter(this::isProviderAvailable)
                .toList();
    }

    /**
     * Checks if a specific provider is available based on its configuration.
     *
     * @param provider the name of the provider
     * @return true if the provider is available, false otherwise
     */
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

    /**
     * Resolves the list of available models for AWS Bedrock.
     *
     * @return a list of Bedrock model IDs
     */
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

    /**
     * Resolves the default model for AWS Bedrock from a list of available models.
     *
     * @param models the list of available models
     * @return the default model ID
     */
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

    /**
     * Resolves the list of available models for Hugging Face.
     *
     * @return a list of Hugging Face model IDs
     */
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
        try {
            return huggingFaceClient.discoverUsableModels(configuredCandidates);
        } catch (ModelDiscoveryException ex) {
            // Keep the selector functional when hosted model validation is temporarily unavailable.
            // The provider status endpoint can still surface the discovery failure more explicitly.
            return configuredCandidates;
        }
    }

    /**
     * Resolves the default model for Hugging Face from a list of available models.
     *
     * @param models the list of available models
     * @return the default model ID
     */
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

    /**
     * Normalizes a model name by trimming whitespace and returning null if blank.
     *
     * @param model the model name to normalize
     * @return the normalized model name, or null if blank
     */
    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        return model.trim();
    }
}
