package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.HuggingFaceClient;
import net.jrodolfo.llm.client.ModelDiscoveryException;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.client.OllamaClientException;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.dto.ProviderStatusResponse;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Provides a compact provider-level troubleshooting summary for the chat UI.
 *
 * <p>This service intentionally separates selector availability from deeper runtime health:
 * the provider dropdown should only include providers configured in the current backend process,
 * while this summary explains whether the selected provider is reachable, missing models, or
 * still misconfigured in ways that are useful to surface near the composer.
 */
@Service
public class ProviderStatusService {

    private static final Duration STATUS_CACHE_TTL = Duration.ofSeconds(15);

    private final ChatModelProviderRegistry chatModelProviderRegistry;
    private final OllamaProperties ollamaProperties;
    private final BedrockProperties bedrockProperties;
    private final HuggingFaceProperties huggingFaceProperties;
    private final OllamaClient ollamaClient;
    private final HuggingFaceClient huggingFaceClient;
    private final Clock clock;
    private final Duration statusCacheTtl;
    private final ConcurrentHashMap<String, CachedStatus> cachedStatuses = new ConcurrentHashMap<>();

    @Autowired
    public ProviderStatusService(
            ChatModelProviderRegistry chatModelProviderRegistry,
            OllamaProperties ollamaProperties,
            BedrockProperties bedrockProperties,
            HuggingFaceProperties huggingFaceProperties,
            OllamaClient ollamaClient,
            @org.springframework.lang.Nullable HuggingFaceClient huggingFaceClient
    ) {
        this(
                chatModelProviderRegistry,
                ollamaProperties,
                bedrockProperties,
                huggingFaceProperties,
                ollamaClient,
                huggingFaceClient,
                Clock.systemUTC(),
                STATUS_CACHE_TTL
        );
    }

    ProviderStatusService(
            ChatModelProviderRegistry chatModelProviderRegistry,
            OllamaProperties ollamaProperties,
            BedrockProperties bedrockProperties,
            HuggingFaceProperties huggingFaceProperties,
            OllamaClient ollamaClient,
            @org.springframework.lang.Nullable HuggingFaceClient huggingFaceClient,
            Clock clock,
            Duration statusCacheTtl
    ) {
        this.chatModelProviderRegistry = chatModelProviderRegistry;
        this.ollamaProperties = ollamaProperties;
        this.bedrockProperties = bedrockProperties;
        this.huggingFaceProperties = huggingFaceProperties;
        this.ollamaClient = ollamaClient;
        this.huggingFaceClient = huggingFaceClient;
        this.clock = clock;
        this.statusCacheTtl = statusCacheTtl;
    }

    public ProviderStatusResponse getProviderStatus(String provider) {
        String resolvedProvider = chatModelProviderRegistry.resolveProviderName(provider);
        chatModelProviderRegistry.get(resolvedProvider);
        Instant now = Instant.now(clock);
        CachedStatus cachedStatus = cachedStatuses.get(resolvedProvider);
        if (cachedStatus != null && now.isBefore(cachedStatus.checkedAt().plus(statusCacheTtl))) {
            return cachedStatus.response();
        }
        ProviderStatusResponse response = switch (resolvedProvider) {
            case "bedrock" -> bedrockStatus();
            case "huggingface" -> huggingFaceStatus();
            case "ollama" -> ollamaStatus();
            default -> throw new InvalidProviderException("Unsupported model provider: " + resolvedProvider);
        };
        cachedStatuses.put(resolvedProvider, new CachedStatus(response, now));
        return response;
    }

    private ProviderStatusResponse ollamaStatus() {
        Instant now = Instant.now(clock);
        try {
            List<String> models = ollamaClient.listModels();
            String defaultModel = normalize(ollamaProperties.defaultModel());
            if (models.isEmpty()) {
                return new ProviderStatusResponse(
                        "ollama",
                        "model_missing",
                        "No Ollama models are installed locally. Run ollama pull llama3:8b and refresh.",
                        now.toString()
                );
            }
            if (defaultModel != null && !models.contains(defaultModel)) {
                return new ProviderStatusResponse(
                        "ollama",
                        "default_model_missing",
                        "Ollama is reachable, but the configured default model is not installed.",
                        now.toString()
                );
            }
            return new ProviderStatusResponse("ollama", "ready", "Ollama is reachable and ready.", now.toString());
        } catch (OllamaClientException ex) {
            return new ProviderStatusResponse(
                    "ollama",
                    "unreachable",
                    "Ollama is not reachable. Check that the local Ollama service is running.",
                    now.toString()
            );
        }
    }

    private ProviderStatusResponse bedrockStatus() {
        Instant now = Instant.now(clock);
        boolean regionConfigured = normalize(bedrockProperties.region()) != null;
        boolean modelConfigured = normalize(bedrockProperties.modelId()) != null;

        if (!regionConfigured || !modelConfigured) {
            return new ProviderStatusResponse(
                    "bedrock",
                    "misconfigured",
                    "Bedrock needs a region and model before requests can succeed.",
                    now.toString()
            );
        }
        return new ProviderStatusResponse("bedrock", "ready", "Bedrock is configured and ready.", now.toString());
    }

    private ProviderStatusResponse huggingFaceStatus() {
        Instant now = Instant.now(clock);
        boolean tokenConfigured = normalize(huggingFaceProperties.apiToken()) != null;
        boolean baseUrlConfigured = normalize(huggingFaceProperties.baseUrl()) != null;
        boolean modelConfigured = normalize(huggingFaceProperties.defaultModel()) != null;

        if (!tokenConfigured || !baseUrlConfigured || !modelConfigured) {
            return new ProviderStatusResponse(
                    "huggingface",
                    "misconfigured",
                    "Hugging Face needs an API token, base URL, and default model before requests can succeed.",
                    now.toString()
            );
        }
        if (huggingFaceClient == null) {
            return new ProviderStatusResponse(
                    "huggingface",
                    "misconfigured",
                    "Hugging Face is selected, but the backend client is not enabled.",
                    now.toString()
            );
        }

        LinkedHashSet<String> configuredCandidates = new LinkedHashSet<>();
        if (huggingFaceProperties.models() != null) {
            for (String model : huggingFaceProperties.models()) {
                String normalized = normalize(model);
                if (normalized != null) {
                    configuredCandidates.add(normalized);
                }
            }
        }
        String defaultModel = normalize(huggingFaceProperties.defaultModel());
        if (defaultModel != null) {
            configuredCandidates.add(defaultModel);
        }
        List<String> configuredModels = List.copyOf(configuredCandidates);
        try {
            HuggingFaceClient.DiscoverySnapshot discoverySnapshot = huggingFaceClient.discoverUsableModelsSnapshot(configuredModels);
            List<String> usableModels = discoverySnapshot.usableModels();
            List<String> rejectedModels = configuredModels.stream()
                    .filter(model -> !usableModels.contains(model))
                    .toList();
            if (!usableModels.isEmpty()) {
                return new ProviderStatusResponse(
                        "huggingface",
                        "ready",
                        "Hugging Face is configured and ready.",
                        discoverySnapshot.checkedAt().toString(),
                        configuredModels,
                        usableModels,
                        rejectedModels
                );
            }
        } catch (ModelDiscoveryException ex) {
            return new ProviderStatusResponse(
                    "huggingface",
                    "unreachable",
                    "Hugging Face model discovery failed. Check the token, base URL, network access, or provider availability.",
                    now.toString(),
                    configuredModels,
                    List.of(),
                    configuredModels
            );
        }
        return new ProviderStatusResponse(
                "huggingface",
                "model_missing",
                "No configured Hugging Face models are currently usable. Check the token, model ids, or provider access.",
                now.toString(),
                configuredModels,
                List.of(),
                configuredModels
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record CachedStatus(
            ProviderStatusResponse response,
            Instant checkedAt
    ) {
    }
}
