package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for chat endpoints.
 *
 * @param streamTimeoutSeconds timeout in seconds for SSE streaming connections
 */
@ConfigurationProperties(prefix = "app.chat")
public record ChatProperties(
        int streamTimeoutSeconds
) {
}
