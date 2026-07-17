package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.client.HuggingFaceClient;
import net.jrodolfo.llm.client.ModelProviderException;
import net.jrodolfo.llm.client.ModelProviderReply;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import net.jrodolfo.llm.util.StringUtils;

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

    /**
     * {@inheritDoc}
     */
    @Override
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

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamingChatResult streamChat(ProviderPrompt prompt, String model, Consumer<String> tokenConsumer) {
        ChatResponse response = chat(prompt, model, null, null, null, null);
        tokenConsumer.accept(response.response());
        return new StreamingChatResult(
                CompletableFuture.completedFuture(response.metadata()),
                () -> {
                }
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveModel(String model) {
        String resolvedModel = StringUtils.normalizeModel(model);
        if (resolvedModel == null) {
            resolvedModel = StringUtils.normalizeModel(huggingFaceProperties.defaultModel());
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

    /**
     * Extracts and filters structured messages from a provider prompt.
     * If no structured messages are present, creates a single user message from the simple prompt string.
     *
     * @param prompt the provider prompt to extract messages from
     * @return a list of structured prompt messages
     */
    private List<ProviderPromptMessage> messagesFor(ProviderPrompt prompt) {
        if (prompt.hasMessages()) {
            return prompt.messages().stream()
                    .filter(message -> message.content() != null && !message.content().isBlank())
                    .toList();
        }
        return List.of(new ProviderPromptMessage("user", prompt.prompt().trim()));
    }

    /**
     * Gets the set of all configured and allowed model identifiers for Hugging Face.
     *
     * @return a set of normalized model identifiers
     */
    private Set<String> configuredModels() {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        if (huggingFaceProperties.models() != null) {
            for (String model : huggingFaceProperties.models()) {
                String normalized = StringUtils.normalizeModel(model);
                if (normalized != null) {
                    models.add(normalized);
                }
            }
        }
        String defaultModel = StringUtils.normalizeModel(huggingFaceProperties.defaultModel());
        if (defaultModel != null) {
            models.add(defaultModel);
        }
        return models;
    }
}
