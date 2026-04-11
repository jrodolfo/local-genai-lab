package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bedrock")
public record BedrockProperties(
        String region,
        String modelId
) {
}
