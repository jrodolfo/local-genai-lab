package net.jrodolfo.llm.config;

import net.jrodolfo.llm.client.AwsSdkBedrockRuntimeGateway;
import net.jrodolfo.llm.client.BedrockCatalogClient;
import net.jrodolfo.llm.client.AwsSdkBedrockCatalogClient;
import net.jrodolfo.llm.client.BedrockRuntimeGateway;
import net.jrodolfo.llm.client.HuggingFaceClient;
import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.provider.BedrockChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import net.jrodolfo.llm.provider.HuggingFaceChatModelProvider;
import net.jrodolfo.llm.provider.OllamaChatModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring configuration class that defines beans for different LLM providers
 * and the registry that manages them.
 */
@Configuration
public class ModelProviderConfig {

    /**
     * Creates a {@link ChatModelProvider} for Ollama.
     *
     * @param ollamaClient the client used to communicate with Ollama
     * @return the Ollama chat model provider
     */
    @Bean
    public ChatModelProvider ollamaChatModelProvider(OllamaClient ollamaClient) {
        return new OllamaChatModelProvider(ollamaClient);
    }

    /**
     * Creates a {@link ChatModelProvider} for Amazon Bedrock, if the region is configured.
     *
     * @param bedrockProperties     properties for Bedrock
     * @param bedrockRuntimeGateway the gateway for Bedrock runtime operations
     * @return the Bedrock chat model provider
     */
    @Bean
    @ConditionalOnProperty(prefix = "bedrock", name = "region")
    public ChatModelProvider bedrockChatModelProvider(
            BedrockProperties bedrockProperties,
            BedrockRuntimeGateway bedrockRuntimeGateway
    ) {
        return new BedrockChatModelProvider(bedrockRuntimeGateway, bedrockProperties);
    }

    /**
     * Creates a {@link HuggingFaceClient} if an API token is provided.
     *
     * @param objectMapper          the object mapper for JSON serialization
     * @param huggingFaceProperties properties for Hugging Face
     * @return the Hugging Face client
     */
    @Bean
    @ConditionalOnExpression("'${huggingface.api-token:}'.trim().length() > 0")
    public HuggingFaceClient huggingFaceClient(
            ObjectMapper objectMapper,
            HuggingFaceProperties huggingFaceProperties
    ) {
        return new HuggingFaceClient(objectMapper, huggingFaceProperties);
    }

    /**
     * Creates a {@link ChatModelProvider} for Hugging Face, if an API token is provided.
     *
     * @param huggingFaceClient     the Hugging Face client
     * @param huggingFaceProperties properties for Hugging Face
     * @return the Hugging Face chat model provider
     */
    @Bean
    @ConditionalOnExpression("'${huggingface.api-token:}'.trim().length() > 0")
    public ChatModelProvider huggingFaceChatModelProvider(
            HuggingFaceClient huggingFaceClient,
            HuggingFaceProperties huggingFaceProperties
    ) {
        return new HuggingFaceChatModelProvider(huggingFaceClient, huggingFaceProperties);
    }

    /**
     * Creates the {@link ChatModelProviderRegistry} and registers available providers.
     *
     * @param appModelProperties                   properties for the chosen provider
     * @param ollamaChatModelProvider              the Ollama provider
     * @param bedrockChatModelProviderProvider     provider for Bedrock (optional)
     * @param huggingFaceChatModelProviderProvider provider for Hugging Face (optional)
     * @return the chat model provider registry
     */
    @Bean
    public ChatModelProviderRegistry chatModelProviderRegistry(
            AppModelProperties appModelProperties,
            ChatModelProvider ollamaChatModelProvider,
            @org.springframework.beans.factory.annotation.Qualifier("bedrockChatModelProvider")
            org.springframework.beans.factory.ObjectProvider<ChatModelProvider> bedrockChatModelProviderProvider,
            @org.springframework.beans.factory.annotation.Qualifier("huggingFaceChatModelProvider")
            org.springframework.beans.factory.ObjectProvider<ChatModelProvider> huggingFaceChatModelProviderProvider
    ) {
        Map<String, ChatModelProvider> providers = new LinkedHashMap<>();
        providers.put("ollama", ollamaChatModelProvider);
        ChatModelProvider bedrockChatModelProvider = bedrockChatModelProviderProvider.getIfAvailable();
        if (bedrockChatModelProvider != null) {
            providers.put("bedrock", bedrockChatModelProvider);
        }
        ChatModelProvider huggingFaceChatModelProvider = huggingFaceChatModelProviderProvider.getIfAvailable();
        if (huggingFaceChatModelProvider != null) {
            providers.put("huggingface", huggingFaceChatModelProvider);
        }
        return new ChatModelProviderRegistry(appModelProperties, providers);
    }

    /**
     * Creates a {@link BedrockRuntimeGateway} for Amazon Bedrock, if the region is configured.
     *
     * @param bedrockProperties properties for Bedrock
     * @return the Bedrock runtime gateway
     */
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

    /**
     * Creates a {@link BedrockCatalogClient} for Amazon Bedrock, if the region is configured.
     *
     * @param bedrockProperties properties for Bedrock
     * @return the Bedrock catalog client
     */
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
