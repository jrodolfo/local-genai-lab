package net.jrodolfo.llm.config;

import net.jrodolfo.llm.client.BedrockRuntimeGateway;
import net.jrodolfo.llm.client.AwsSdkBedrockRuntimeGateway;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.BedrockChatModelProvider;
import net.jrodolfo.llm.provider.OllamaChatModelProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class ModelProviderConfig {

    @Bean
    public ChatModelProvider chatModelProvider(
            AppModelProperties appModelProperties,
            OllamaClient ollamaClient,
            BedrockProperties bedrockProperties
    ) {
        String provider = appModelProperties.provider();
        if (provider == null || provider.isBlank() || provider.equalsIgnoreCase("ollama")) {
            return new OllamaChatModelProvider(ollamaClient);
        }
        if (provider.equalsIgnoreCase("bedrock")) {
            String region = bedrockProperties.region();
            if (region == null || region.isBlank()) {
                throw new IllegalStateException("BEDROCK_REGION must be configured when using the 'bedrock' provider.");
            }
            Region bedrockRegion = Region.of(region);
            BedrockRuntimeGateway bedrockRuntimeGateway = new AwsSdkBedrockRuntimeGateway(
                    BedrockRuntimeClient.builder()
                            .region(bedrockRegion)
                            .build(),
                    BedrockRuntimeAsyncClient.builder()
                            .region(bedrockRegion)
                            .build()
            );
            return new BedrockChatModelProvider(bedrockRuntimeGateway, bedrockProperties);
        }
        throw new IllegalStateException("Unsupported model provider: " + provider + ". Supported providers are 'ollama' and 'bedrock'.");
    }
}
