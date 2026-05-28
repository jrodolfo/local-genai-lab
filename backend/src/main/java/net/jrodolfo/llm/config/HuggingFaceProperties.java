package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for Hugging Face Inference API.
 *
 * @param baseUrl               the base URL for the Hugging Face API
 * @param apiToken              the API token for authentication
 * @param defaultModel          the default model to use for inference
 * @param models                a list of supported models
 * @param connectTimeoutSeconds timeout in seconds for establishing a connection
 * @param readTimeoutSeconds    timeout in seconds for reading a response
 */
@ConfigurationProperties(prefix = "huggingface")
public record HuggingFaceProperties(
        String baseUrl,
        String apiToken,
        String defaultModel,
        List<String> models,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
}
