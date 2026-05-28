package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.client.HuggingFaceClient;
import net.jrodolfo.llm.client.ModelProviderException;
import net.jrodolfo.llm.client.ModelProviderReply;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Implementation of {@link ChatModelProvider} for hosted Hugging Face models.
 * Backed by a curated list of configured models.
 */
public class HuggingFaceChatModelProvider implements ChatModelProvider {

    private final HuggingFaceClient huggingFaceClient;
    private final HuggingFaceProperties huggingFaceProperties;

    /**
     * Constructs a new HuggingFaceChatModelProvider.
     *
     * @param huggingFaceClient     the client to communicate with Hugging Face API
     * @param huggingFaceProperties the configuration properties for Hugging Face
     */
    public HuggingFaceChatModelProvider(HuggingFaceClient huggingFaceClient, HuggingFaceProperties huggingFaceProperties) {
        this.huggingFaceClient = huggingFaceClient;
        this.huggingFaceProperties = huggingFaceProperties;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public ChatResponse chat(
            ProviderPrompt prompt,
            String model,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            String sessionId,
            PendingToolCallResponse pendingTool
    ) {
        String resolvedModel = resolveModel(model);
        ModelProviderReply reply = huggingFaceClient.chat(messagesFor(prompt), resolvedModel);
        return new ChatResponse(reply.text(), resolvedModel, toolMetadata, toolResult, sessionId, pendingTool, reply.metadata());
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public StreamingChatResult streamChat(ProviderPrompt prompt, String model, Consumer<String> tokenConsumer) {
        ChatResponse response = chat(prompt, model, null, null, null, null);
        tokenConsumer.accept(response.response());
        return new StreamingChatResult(
                CompletableFuture.completedFuture(response.metadata()),
                () -> {
                }
        );
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public String resolveModel(String model) {
        String resolvedModel = normalizeModel(model);
        if (resolvedModel == null) {
            resolvedModel = normalizeModel(huggingFaceProperties.defaultModel());
        }
        if (resolvedModel == null) {
            throw new ModelProviderException(
                    "No Hugging Face model is configured. Set HUGGINGFACE_DEFAULT_MODEL or provide a model in the request."
            );
        }

        Set<String> allowedModels = configuredModels();
        if (!allowedModels.isEmpty() && !allowedModels.contains(resolvedModel)) {
            throw new ModelProviderException(
                    "Unsupported Hugging Face model: %s. Supported models are: %s"
                            .formatted(resolvedModel, allowedModels)
            );
        }
        return resolvedModel;
    }

    private List<ProviderPromptMessage> messagesFor(ProviderPrompt prompt) {
        if (prompt.hasMessages()) {
            return prompt.messages().stream()
                    .filter(message -> message.content() != null && !message.content().isBlank())
                    .toList();
        }
        return List.of(new ProviderPromptMessage("user", prompt.prompt().trim()));
    }

    private Set<String> configuredModels() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (huggingFaceProperties.models() != null) {
            for (String model : huggingFaceProperties.models()) {
                String normalized = normalizeModel(model);
                if (normalized != null) {
                    models.add(normalized);
                }
            }
        }
        String defaultModel = normalizeModel(huggingFaceProperties.defaultModel());
        if (defaultModel != null) {
            models.add(defaultModel);
        }
        return models;
    }

    private String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        return model.trim();
    }
}
