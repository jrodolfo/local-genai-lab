package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record AppStorageProperties(
        String sessionsDirectory,
        String reportsDirectory
) {
}
