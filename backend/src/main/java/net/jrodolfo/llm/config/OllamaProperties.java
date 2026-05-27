package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Ollama.
 * 
 * @param baseUrl the base URL for the Ollama server
 * @param defaultModel the default model to use for inference
 * @param connectTimeoutSeconds timeout in seconds for establishing a connection
 * @param readTimeoutSeconds timeout in seconds for reading a response
 */
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        String baseUrl,
        String defaultModel,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
}
