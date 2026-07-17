package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Cross-Origin Resource Sharing (CORS).
 *
 * @param allowedOrigins comma-separated list of allowed origins
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        String allowedOrigins
) {
    /**
     * Splits the configured origins into an array.
     *
     * @return trimmed origin strings
     */
    public String[] originsArray() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}
