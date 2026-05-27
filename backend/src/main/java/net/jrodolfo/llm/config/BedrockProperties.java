package net.jrodolfo.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Amazon Bedrock.
 * 
 * @param region the AWS region where Bedrock is used (e.g., "us-east-1")
 * @param modelId the default model ID to use with Bedrock
 */
@ConfigurationProperties(prefix = "bedrock")
public record BedrockProperties(
        String region,
        String modelId
) {
}
