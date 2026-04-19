package net.jrodolfo.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.provider.ProviderPromptMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Minimal client for Hugging Face's hosted chat-completions endpoint.
 *
 * <p>This client handles both normal chat requests and the lightweight candidate-model probes used
 * by the frontend model selector. The probe result is cached briefly so the UI can refresh the
 * usable subset without turning every selector refresh into a full round of remote validation.
 */
public class HuggingFaceClient {

    private final ObjectMapper objectMapper;
    private final HuggingFaceProperties huggingFaceProperties;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Duration discoveryCacheTtl;
    // Cache the last validated probe context for a short period so provider status and model
    // discovery can share the same remote validation result without repeatedly probing the
    // hosted endpoint. The cache must remember the candidate set, not only the usable subset,
    // otherwise an expanded or changed request can accidentally reuse stale probe results.
    private volatile List<String> cachedUsableModels = List.of();
    private volatile Set<String> cachedCandidateModels = Set.of();
    private volatile Instant cachedUsableModelsAt = Instant.EPOCH;

    public HuggingFaceClient(ObjectMapper objectMapper, HuggingFaceProperties huggingFaceProperties) {
        this(
                objectMapper,
                huggingFaceProperties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(1, huggingFaceProperties.connectTimeoutSeconds())))
                        .build(),
                Clock.systemUTC(),
                Duration.ofSeconds(30)
        );
    }

    HuggingFaceClient(
            ObjectMapper objectMapper,
            HuggingFaceProperties huggingFaceProperties,
            HttpClient httpClient
    ) {
        this(objectMapper, huggingFaceProperties, httpClient, Clock.systemUTC(), Duration.ofSeconds(30));
    }

    HuggingFaceClient(
            ObjectMapper objectMapper,
            HuggingFaceProperties huggingFaceProperties,
            HttpClient httpClient,
            Clock clock,
            Duration discoveryCacheTtl
    ) {
        this.objectMapper = objectMapper;
        this.huggingFaceProperties = huggingFaceProperties;
        this.httpClient = httpClient;
        this.clock = clock;
        this.discoveryCacheTtl = discoveryCacheTtl;
    }

    public ModelProviderReply chat(List<ProviderPromptMessage> messages, String model) {
        long startedAt = System.nanoTime();
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", messages.stream()
                            .map(message -> Map.of("role", message.role(), "content", message.content()))
                            .toList(),
                    "stream", false
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(huggingFaceProperties.baseUrl()))
                    .timeout(Duration.ofSeconds(Math.max(1, huggingFaceProperties.readTimeoutSeconds())))
                    .header("Authorization", "Bearer " + huggingFaceProperties.apiToken().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelProviderException(parseErrorMessage(response.body(), response.statusCode()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode usage = root.path("usage");
            return new ModelProviderReply(
                    parseResponseText(root),
                    new ModelProviderMetadata(
                            "huggingface",
                            model,
                            textValue(root.path("choices").path(0).path("finish_reason")),
                            intValue(usage.path("prompt_tokens")),
                            intValue(usage.path("completion_tokens")),
                            intValue(usage.path("total_tokens")),
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                            null,
                            null,
                            null
                    )
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModelProviderException("Hugging Face request was interrupted.", ex);
        } catch (IOException ex) {
            throw new ModelProviderException(buildRequestFailureMessage(ex), ex);
        }
    }

    /**
     * Returns the subset of configured candidate models that are currently usable through the
     * configured hosted chat endpoint. The result is cached briefly so the UI can refresh provider
     * state without triggering repeated probe requests.
     */
    public List<String> discoverUsableModels(List<String> candidateModels) {
        return discoverUsableModelsSnapshot(candidateModels).usableModels();
    }

    public DiscoverySnapshot discoverUsableModelsSnapshot(List<String> candidateModels) {
        List<String> normalizedCandidates = candidateModels == null ? List.of() : candidateModels.stream()
                .filter(model -> model != null && !model.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedCandidates.isEmpty()) {
            return new DiscoverySnapshot(List.of(), Instant.now(clock));
        }

        Instant now = Instant.now(clock);
        Set<String> candidateSet = Set.copyOf(normalizedCandidates);
        if (cachedCandidateModels.equals(candidateSet) && now.isBefore(cachedUsableModelsAt.plus(discoveryCacheTtl))) {
            return new DiscoverySnapshot(
                    normalizedCandidates.stream()
                            .filter(cachedUsableModels::contains)
                            .toList(),
                    cachedUsableModelsAt
            );
        }

        List<String> usableModels = new ArrayList<>();
        for (String model : normalizedCandidates) {
            if (isModelUsable(model)) {
                usableModels.add(model);
            }
        }
        List<String> immutableUsableModels = List.copyOf(usableModels);
        cachedUsableModels = immutableUsableModels;
        cachedCandidateModels = candidateSet;
        cachedUsableModelsAt = now;
        return new DiscoverySnapshot(immutableUsableModels, now);
    }

    private boolean isModelUsable(String model) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", "Reply with OK.")),
                    "max_tokens", 1,
                    "stream", false
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(huggingFaceProperties.baseUrl()))
                    .timeout(Duration.ofSeconds(Math.max(1, huggingFaceProperties.readTimeoutSeconds())))
                    .header("Authorization", "Bearer " + huggingFaceProperties.apiToken().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModelDiscoveryException("Hugging Face model discovery was interrupted.", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ModelDiscoveryException("Hugging Face model discovery failed.", ex);
        }
    }

    private String parseResponseText(JsonNode root) {
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode part : contentNode) {
                JsonNode text = part.path("text");
                if (text.isTextual()) {
                    builder.append(text.asText());
                }
            }
            String combined = builder.toString();
            if (!combined.isBlank()) {
                return combined;
            }
        }
        throw new ModelProviderException("Hugging Face response did not contain chat content.");
    }

    private String parseErrorMessage(String responseBody, int statusCode) {
        String providerMessage = null;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorNode = root.path("error");
            if (errorNode.isTextual() && !errorNode.asText().isBlank()) {
                providerMessage = errorNode.asText();
            }
            JsonNode messageNode = errorNode.path("message");
            if (messageNode.isTextual() && !messageNode.asText().isBlank()) {
                providerMessage = messageNode.asText();
            }
        } catch (IOException ignored) {
            // Fall through to the generic error below.
        }

        return switch (statusCode) {
            case 401, 403 -> "Hugging Face authentication failed. Check HUGGINGFACE_API_TOKEN and model access."
                    + appendProviderMessage(providerMessage);
            case 404 -> "The selected Hugging Face model or endpoint is unavailable."
                    + appendProviderMessage(providerMessage);
            case 429 -> "Hugging Face rate limited the request. Wait and try again."
                    + appendProviderMessage(providerMessage);
            default -> statusCode >= 500
                    ? "Hugging Face is currently unavailable."
                    + appendProviderMessage(providerMessage)
                    : "Hugging Face request failed with status " + statusCode + "."
                    + appendProviderMessage(providerMessage);
        };
    }

    private String buildRequestFailureMessage(IOException ex) {
        if (ex instanceof HttpTimeoutException) {
            return "Hugging Face request timed out after " + Math.max(1, huggingFaceProperties.readTimeoutSeconds())
                    + "s. Try again or increase HUGGINGFACE_READ_TIMEOUT_SECONDS.";
        }
        String message = ex.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("connection refused")
                    || lowerMessage.contains("unknown host")
                    || lowerMessage.contains("name or service not known")
                    || lowerMessage.contains("failed to connect")) {
                return "Hugging Face endpoint is unreachable. Check network access and HUGGINGFACE_BASE_URL.";
            }
        }
        return "Hugging Face request failed. Check network access, model availability, or provider configuration.";
    }

    private String appendProviderMessage(String providerMessage) {
        if (providerMessage == null || providerMessage.isBlank()) {
            return "";
        }
        return " Provider message: " + providerMessage;
    }

    private Integer intValue(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }

    private String textValue(JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    public record DiscoverySnapshot(
            List<String> usableModels,
            Instant checkedAt
    ) {
    }
}
