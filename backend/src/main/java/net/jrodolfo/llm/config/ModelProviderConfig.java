package net.jrodolfo.llm.config;

import net.jrodolfo.llm.client.BedrockRuntimeGateway;
import net.jrodolfo.llm.client.AwsSdkBedrockRuntimeGateway;
import net.jrodolfo.llm.client.BedrockCatalogClient;
import net.jrodolfo.llm.client.AwsSdkBedrockCatalogClient;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.BedrockChatModelProvider;
import net.jrodolfo.llm.provider.OllamaChatModelProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
public class ModelProviderConfig {

    @Bean
    public ChatModelProvider chatModelProvider(
            AppModelProperties appModelProperties,
            OllamaClient ollamaClient,
            BedrockProperties bedrockProperties,
            BedrockRuntimeGateway bedrockRuntimeGateway
    ) {
        String provider = appModelProperties.provider();
        if (provider == null || provider.isBlank() || provider.equalsIgnoreCase("ollama")) {
            return new OllamaChatModelProvider(ollamaClient);
        }
        if (provider.equalsIgnoreCase("bedrock")) {
            return new BedrockChatModelProvider(bedrockRuntimeGateway, bedrockProperties);
        }
        throw new IllegalStateException("Unsupported model provider: " + provider + ". Supported providers are 'ollama' and 'bedrock'.");
    }

    @Bean
    @ConditionalOnProperty(prefix = "bedrock", name = "region")
    public BedrockRuntimeGateway bedrockRuntimeGateway(BedrockProperties bedrockProperties) {
        Region bedrockRegion = Region.of(bedrockProperties.region());
        return new AwsSdkBedrockRuntimeGateway(
                BedrockRuntimeClient.builder()
                        .region(bedrockRegion)
                        .build(),
                BedrockRuntimeAsyncClient.builder()
                        .region(bedrockRegion)
                        .build()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "bedrock", name = "region")
    public BedrockCatalogClient bedrockCatalogClient(BedrockProperties bedrockProperties) {
        Region bedrockRegion = Region.of(bedrockProperties.region());
        return new AwsSdkBedrockCatalogClient(
                BedrockClient.builder()
                        .region(bedrockRegion)
                        .build()
        );
    }
}
