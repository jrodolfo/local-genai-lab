package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the application's LLM model provider.
 * 
 * @param provider the name of the LLM provider to use (e.g., "ollama", "bedrock", "huggingface")
 */
@ConfigurationProperties(prefix = "app.model")
public record AppModelProperties(
        String provider
) {
}
