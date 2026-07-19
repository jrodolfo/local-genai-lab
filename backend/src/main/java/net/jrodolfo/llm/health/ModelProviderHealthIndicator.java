package net.jrodolfo.llm.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import net.jrodolfo.llm.config.OllamaProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Health indicator for the active model provider (Ollama, Bedrock, or Hugging Face).
 * <p>
 * This indicator checks the configuration and readiness of the selected provider.
 */
@Component("modelProvider")
public class ModelProviderHealthIndicator implements HealthIndicator {

    private final AppModelProperties appModelProperties;
    private final OllamaProperties ollamaProperties;
    private final BedrockProperties bedrockProperties;
    private final HuggingFaceProperties huggingFaceProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Supplier<Boolean> bedrockCredentialsResolver;

    /**
     * Constructs a new ModelProviderHealthIndicator using Spring-managed beans.
     *
     * @param appModelProperties    the application model properties.
     * @param ollamaProperties      the Ollama configuration properties.
     * @param bedrockProperties     the Bedrock configuration properties.
     * @param huggingFaceProperties the Hugging Face configuration properties.
     * @param objectMapperProvider  the provider for the Jackson object mapper.
     */
    @Autowired
    public ModelProviderHealthIndicator(
            AppModelProperties appModelProperties,
            OllamaProperties ollamaProperties,
            BedrockProperties bedrockProperties,
            HuggingFaceProperties huggingFaceProperties,
            ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        this(
                appModelProperties,
                ollamaProperties,
                bedrockProperties,
                huggingFaceProperties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(1, ollamaProperties.connectTimeoutSeconds())))
                        .build(),
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                () -> {
                    AwsCredentialsProvider provider = DefaultCredentialsProvider.create();
                    provider.resolveCredentials();
                    return true;
                }
        );
    }

    /**
     * Constructs a new ModelProviderHealthIndicator with the specified dependencies.
     *
     * @param appModelProperties         the application model properties.
     * @param ollamaProperties           the Ollama configuration properties.
     * @param bedrockProperties          the Bedrock configuration properties.
     * @param huggingFaceProperties      the Hugging Face configuration properties.
     * @param httpClient                 the HTTP client for network checks.
     * @param objectMapper               the Jackson object mapper for JSON parsing.
     * @param bedrockCredentialsResolver the supplier for Bedrock credentials resolution.
     */
    ModelProviderHealthIndicator(
            AppModelProperties appModelProperties,
            OllamaProperties ollamaProperties,
            BedrockProperties bedrockProperties,
            HuggingFaceProperties huggingFaceProperties,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Supplier<Boolean> bedrockCredentialsResolver
    ) {
        this.appModelProperties = appModelProperties;
        this.ollamaProperties = ollamaProperties;
        this.bedrockProperties = bedrockProperties;
        this.huggingFaceProperties = huggingFaceProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.bedrockCredentialsResolver = bedrockCredentialsResolver;
    }

    /**
     * Performs the health check for the active model provider.
     *
     * @return the health status and details of the model provider.
     */
    @Override
    public Health health() {
        String provider = normalizeProvider(appModelProperties.provider());
        return switch (provider) {
            case "bedrock" -> bedrockHealth();
            case "huggingface" -> huggingFaceHealth();
            case "ollama" -> ollamaHealth();
            default -> Health.down()
                    .withDetail("provider", provider)
                    .withDetail("error", "Unsupported model provider.")
                    .build();
        };
    }

    /**
     * Checks the health of the Amazon Bedrock provider.
     *
     * @return the health status and configuration details for Bedrock.
     */
    private Health bedrockHealth() {
        boolean regionConfigured = bedrockProperties.region() != null && !bedrockProperties.region().isBlank();
        boolean modelConfigured = bedrockProperties.modelId() != null && !bedrockProperties.modelId().isBlank();
        boolean credentialsResolved = false;

        if (regionConfigured && modelConfigured) {
            try {
                credentialsResolved = Boolean.TRUE.equals(bedrockCredentialsResolver.get());
            } catch (RuntimeException ignored) {
                credentialsResolved = false;
            }
        }

        if (!regionConfigured || !modelConfigured || !credentialsResolved) {
            return Health.down()
                    .withDetail("provider", "bedrock")
                    .withDetail("regionConfigured", regionConfigured)
                    .withDetail("modelConfigured", modelConfigured)
                    .withDetail("credentialsResolved", credentialsResolved)
                    .withDetail("status", "misconfigured")
                    .withDetail("error", "BEDROCK_REGION, BEDROCK_MODEL_ID, and AWS credentials must all be configured.")
                    .build();
        }

        return Health.up()
                .withDetail("provider", "bedrock")
                .withDetail("status", "ready")
                .withDetail("region", bedrockProperties.region())
                .withDetail("modelId", bedrockProperties.modelId())
                .withDetail("credentialsResolved", true)
                .build();
    }

    /**
     * Checks the health of the Ollama provider by querying its API and verifying the default model's presence.
     *
     * @return the health status and connection details for Ollama.
     */
    private Health ollamaHealth() {
        String baseUrl = ollamaProperties.baseUrl();
        String defaultModel = ollamaProperties.defaultModel();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(Math.max(1, ollamaProperties.connectTimeoutSeconds())))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                boolean modelPresent = ollamaModelPresent(response.body(), defaultModel);
                if (!modelPresent) {
                    return Health.up()
                            .withDetail("provider", "ollama")
                            .withDetail("status", "not-ready")
                            .withDetail("baseUrl", baseUrl)
                            .withDetail("defaultModel", defaultModel)
                            .withDetail("reachable", true)
                            .withDetail("ready", false)
                            .withDetail("modelPresent", false)
                            .withDetail("error", "Ollama is reachable, but the configured default model is not installed.")
                            .build();
                }
                return Health.up()
                        .withDetail("provider", "ollama")
                        .withDetail("status", "ready")
                        .withDetail("baseUrl", baseUrl)
                        .withDetail("defaultModel", defaultModel)
                        .withDetail("reachable", true)
                        .withDetail("ready", true)
                        .withDetail("modelPresent", true)
                        .build();
            }
            return Health.up()
                    .withDetail("provider", "ollama")
                    .withDetail("status", "unreachable")
                    .withDetail("baseUrl", baseUrl)
                    .withDetail("defaultModel", defaultModel)
                    .withDetail("reachable", false)
                    .withDetail("ready", false)
                    .withDetail("statusCode", response.statusCode())
                    .withDetail("error", "Ollama tags endpoint returned a non-success status.")
                    .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Health.up()
                    .withDetail("provider", "ollama")
                    .withDetail("status", "interrupted")
                    .withDetail("baseUrl", baseUrl)
                    .withDetail("ready", false)
                    .withDetail("error", "Ollama reachability check was interrupted.")
                    .build();
        } catch (IOException | IllegalArgumentException ex) {
            return Health.up()
                    .withDetail("provider", "ollama")
                    .withDetail("status", "unreachable")
                    .withDetail("baseUrl", baseUrl)
                    .withDetail("defaultModel", defaultModel)
                    .withDetail("reachable", false)
                    .withDetail("ready", false)
                    .withDetail("error", failureMessage(ex))
                    .build();
        }
    }

    /**
     * Checks the health of the Hugging Face provider.
     *
     * @return the health status and configuration details for Hugging Face.
     */
    private Health huggingFaceHealth() {
        boolean tokenConfigured = huggingFaceProperties.apiToken() != null && !huggingFaceProperties.apiToken().isBlank();
        boolean baseUrlConfigured = huggingFaceProperties.baseUrl() != null && !huggingFaceProperties.baseUrl().isBlank();
        boolean modelConfigured = huggingFaceProperties.defaultModel() != null && !huggingFaceProperties.defaultModel().isBlank();

        if (!tokenConfigured || !baseUrlConfigured || !modelConfigured) {
            return Health.down()
                    .withDetail("provider", "huggingface")
                    .withDetail("baseUrlConfigured", baseUrlConfigured)
                    .withDetail("tokenConfigured", tokenConfigured)
                    .withDetail("modelConfigured", modelConfigured)
                    .withDetail("status", "misconfigured")
                    .withDetail("error", "HUGGINGFACE_API_TOKEN, HUGGINGFACE_BASE_URL, and HUGGINGFACE_DEFAULT_MODEL must all be configured.")
                    .build();
        }

        return Health.up()
                .withDetail("provider", "huggingface")
                .withDetail("status", "ready")
                .withDetail("baseUrl", huggingFaceProperties.baseUrl())
                .withDetail("defaultModel", huggingFaceProperties.defaultModel())
                .build();
    }

    /**
     * Checks if the specified model is present in the Ollama response body.
     *
     * @param responseBody the JSON response from the Ollama tags API.
     * @param defaultModel the name of the model to look for.
     * @return {@code true} if the model is present, {@code false} otherwise.
     */
    private boolean ollamaModelPresent(String responseBody, String defaultModel) {
        if (defaultModel == null || defaultModel.isBlank()) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode models = root.path("models");
            if (!models.isArray()) {
                return false;
            }
            for (JsonNode model : models) {
                String name = model.path("name").asText();
                if (defaultModel.equals(name)) {
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Normalizes the provider name to a standard format.
     *
     * @param provider the raw provider name from the properties.
     * @return the normalized, lowercase provider name.
     */
    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "ollama";
        }
        return provider.trim().toLowerCase();
    }

    private String failureMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message;
    }
}
