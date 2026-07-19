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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

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
 *   <li>Hugging Face returns configured candidates without blocking on hosted validation.</li>
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
    private final Environment environment;
    private final Supplier<Boolean> bedrockCredentialsResolver;

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
    @Autowired
    public AvailableModelsService(
            ChatModelProviderRegistry chatModelProviderRegistry,
            OllamaProperties ollamaProperties,
            BedrockProperties bedrockProperties,
            HuggingFaceProperties huggingFaceProperties,
            OllamaClient ollamaClient,
            @Nullable BedrockCatalogClient bedrockCatalogClient,
            @Nullable HuggingFaceClient huggingFaceClient,
            Environment environment
    ) {
        this(
                chatModelProviderRegistry,
                ollamaProperties,
                bedrockProperties,
                huggingFaceProperties,
                ollamaClient,
                bedrockCatalogClient,
                huggingFaceClient,
                environment,
                () -> {
                    AwsCredentialsProvider provider = DefaultCredentialsProvider.create();
                    provider.resolveCredentials();
                    return true;
                }
        );
    }

    AvailableModelsService(
            ChatModelProviderRegistry chatModelProviderRegistry,
            OllamaProperties ollamaProperties,
            BedrockProperties bedrockProperties,
            HuggingFaceProperties huggingFaceProperties,
            OllamaClient ollamaClient,
            @Nullable BedrockCatalogClient bedrockCatalogClient,
            @Nullable HuggingFaceClient huggingFaceClient,
            Environment environment,
            Supplier<Boolean> bedrockCredentialsResolver
    ) {
        this.chatModelProviderRegistry = chatModelProviderRegistry;
        this.ollamaProperties = ollamaProperties;
        this.bedrockProperties = bedrockProperties;
        this.huggingFaceProperties = huggingFaceProperties;
        this.ollamaClient = ollamaClient;
        this.bedrockCatalogClient = bedrockCatalogClient;
        this.huggingFaceClient = huggingFaceClient;
        this.environment = environment;
        this.bedrockCredentialsResolver = bedrockCredentialsResolver;
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
                    resolveInstanceName(),
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
                    resolveInstanceName(),
                    resolveDefaultHuggingFaceModel(models),
                    models
            );
        }

        List<String> models = List.copyOf(new LinkedHashSet<>(ollamaClient.listModels()));
        return new AvailableModelsResponse(
                "ollama",
                chatModelProviderRegistry.defaultProvider(),
                availableProviders,
                resolveInstanceName(),
                resolveDefaultOllamaModel(models),
                models
        );
    }

    private String resolveInstanceName() {
        return normalizeModel(environment.getProperty("app.instance-name"));
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
                    && normalizeModel(bedrockProperties.modelId()) != null
                    && bedrockCredentialsAvailable();
            case "huggingface" -> normalizeModel(huggingFaceProperties.apiToken()) != null
                    && normalizeModel(huggingFaceProperties.baseUrl()) != null
                    && normalizeModel(huggingFaceProperties.defaultModel()) != null;
            case "ollama" -> true;
            default -> false;
        };
    }

    private boolean bedrockCredentialsAvailable() {
        try {
            return Boolean.TRUE.equals(bedrockCredentialsResolver.get());
        } catch (RuntimeException ex) {
            return false;
        }
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
     * Resolves the default model for Ollama from the installed model list.
     *
     * @param models the installed Ollama models
     * @return the configured default when installed, otherwise the first installed model, or null when none exist
     */
    private String resolveDefaultOllamaModel(List<String> models) {
        String configuredModelId = normalizeModel(ollamaProperties.defaultModel());
        if (configuredModelId != null && models.contains(configuredModelId)) {
            return configuredModelId;
        }
        if (!models.isEmpty()) {
            return models.getFirst();
        }
        return null;
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
        // Model selection should be fast and based on explicit configuration. The provider status
        // endpoint performs slower hosted validation and reports usable/rejected models.
        return List.copyOf(new ArrayList<>(models));
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
