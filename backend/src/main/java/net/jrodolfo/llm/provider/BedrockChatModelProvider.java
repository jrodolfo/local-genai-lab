package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.client.BedrockRuntimeGateway;
import net.jrodolfo.llm.client.ModelProviderReply;
import net.jrodolfo.llm.client.ModelProviderException;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;

import java.util.function.Consumer;

public class BedrockChatModelProvider implements ChatModelProvider {

    private final BedrockRuntimeGateway bedrockRuntimeGateway;
    private final BedrockProperties bedrockProperties;

    public BedrockChatModelProvider(BedrockRuntimeGateway bedrockRuntimeGateway, BedrockProperties bedrockProperties) {
        this.bedrockRuntimeGateway = bedrockRuntimeGateway;
        this.bedrockProperties = bedrockProperties;
    }

    @Override
    public ChatResponse chat(
            String message,
            String model,
            ChatToolMetadata toolMetadata,
            String sessionId,
            PendingToolCallResponse pendingTool
    ) {
        String normalizedMessage = message.trim();
        String resolvedModel = resolveModel(model);
        ModelProviderReply reply = bedrockRuntimeGateway.converse(normalizedMessage, resolvedModel);
        return new ChatResponse(reply.text(), resolvedModel, toolMetadata, sessionId, pendingTool, reply.metadata());
    }

    @Override
    public ModelProviderMetadata streamChat(String message, String model, Consumer<String> tokenConsumer) {
        String normalizedMessage = message.trim();
        String resolvedModel = resolveModel(model);
        return bedrockRuntimeGateway.converseStream(normalizedMessage, resolvedModel, tokenConsumer);
    }

    @Override
    public String resolveModel(String model) {
        if (model == null || model.isBlank()) {
            if (bedrockProperties.modelId() == null || bedrockProperties.modelId().isBlank()) {
                throw new ModelProviderException("No Bedrock model is configured. Set BEDROCK_MODEL_ID or provide a model in the request.");
            }
            return bedrockProperties.modelId();
        }
        return model.trim();
    }
}
