package net.jrodolfo.llm.client;

import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.provider.ProviderPromptMessage;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Bedrock runtime adapter backed by the AWS SDK v2 converse APIs.
 *
 * <p>The gateway keeps Bedrock-specific event handling out of the provider layer and converts both
 * normal and streaming responses into the project's provider metadata model.
 */
public class AwsSdkBedrockRuntimeGateway implements BedrockRuntimeGateway {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

    public AwsSdkBedrockRuntimeGateway(
            BedrockRuntimeClient bedrockRuntimeClient,
            BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient
    ) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.bedrockRuntimeAsyncClient = bedrockRuntimeAsyncClient;
    }

    @Override
    public ModelProviderReply converse(String prompt, String modelId) {
        try {
            long startedAt = System.currentTimeMillis();
            ConverseRequest request = buildConverseRequest(buildUserMessage(prompt), List.of(), modelId);
            ConverseResponse response = bedrockRuntimeClient.converse(request);
            return toReply(response, modelId, startedAt);
        } catch (ValidationException ex) {
            throw new ModelProviderException("Bedrock request validation failed: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new ModelProviderException("Failed to call Bedrock converse endpoint.", ex);
        }
    }

    @Override
    public ModelProviderReply converse(List<ProviderPromptMessage> messages, String modelId) {
        try {
            long startedAt = System.currentTimeMillis();
            StructuredBedrockPrompt prompt = toStructuredPrompt(messages);
            ConverseRequest request = buildConverseRequest(prompt.messages(), prompt.system(), modelId);
            ConverseResponse response = bedrockRuntimeClient.converse(request);
            return toReply(response, modelId, startedAt);
        } catch (ValidationException ex) {
            throw new ModelProviderException("Bedrock request validation failed: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new ModelProviderException("Failed to call Bedrock converse endpoint.", ex);
        }
    }

    @Override
    public CompletableFuture<ModelProviderMetadata> converseStream(String prompt, String modelId, Consumer<String> chunkConsumer) {
        try {
            ConverseStreamRequest request = buildConverseStreamRequest(buildUserMessage(prompt), List.of(), modelId);
            return stream(request, modelId, chunkConsumer);
        } catch (ValidationException ex) {
            throw new ModelProviderException("Bedrock request validation failed: " + ex.getMessage(), ex);
        } catch (SdkException ex) {
            throw new ModelProviderException("Failed to stream from Bedrock.", ex);
        } catch (RuntimeException ex) {
            throw new ModelProviderException("Failed to stream from Bedrock.", ex);
        }
    }

    @Override
    public CompletableFuture<ModelProviderMetadata> converseStream(
            List<ProviderPromptMessage> messages,
            String modelId,
            Consumer<String> chunkConsumer
    ) {
        try {
            StructuredBedrockPrompt prompt = toStructuredPrompt(messages);
            ConverseStreamRequest request = buildConverseStreamRequest(prompt.messages(), prompt.system(), modelId);
            return stream(request, modelId, chunkConsumer);
        } catch (ValidationException ex) {
            throw new ModelProviderException("Bedrock request validation failed: " + ex.getMessage(), ex);
        } catch (SdkException ex) {
            throw new ModelProviderException("Failed to stream from Bedrock.", ex);
        } catch (RuntimeException ex) {
            throw new ModelProviderException("Failed to stream from Bedrock.", ex);
        }
    }

    private CompletableFuture<ModelProviderMetadata> stream(
            ConverseStreamRequest request,
            String modelId,
            Consumer<String> chunkConsumer
    ) {
        try {
            long startedAt = System.currentTimeMillis();
            AtomicReference<String> stopReason = new AtomicReference<>();
            AtomicReference<TokenUsage> usage = new AtomicReference<>();
            AtomicReference<ConverseStreamMetrics> metrics = new AtomicReference<>();
            CompletableFuture<ModelProviderMetadata> metadataFuture = new CompletableFuture<>();

            ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                    .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                            .onContentBlockDelta(event -> forwardChunk(event, chunkConsumer))
                            .onMessageStop(event -> captureStopReason(event, stopReason))
                            .onMetadata(event -> captureMetadata(event, usage, metrics))
                            .build())
                    .onError(error -> metadataFuture.completeExceptionally(
                            new ModelProviderException("Failed to stream from Bedrock.", error)
                    ))
                    .onComplete(() -> {
                        TokenUsage finalUsage = usage.get();
                        ConverseStreamMetrics finalMetrics = metrics.get();
                        metadataFuture.complete(new ModelProviderMetadata(
                                "bedrock",
                                modelId,
                                stopReason.get(),
                                finalUsage != null ? finalUsage.inputTokens() : null,
                                finalUsage != null ? finalUsage.outputTokens() : null,
                                finalUsage != null ? finalUsage.totalTokens() : null,
                                System.currentTimeMillis() - startedAt,
                                finalMetrics != null ? finalMetrics.latencyMs() : null,
                                null,
                                null
                        ));
                    })
                    .build();

            CompletableFuture<Void> streamFuture = bedrockRuntimeAsyncClient.converseStream(request, handler);
            // Cancellation is driven by the controller when the SSE client disconnects.
            metadataFuture.whenComplete((ignored, throwable) -> {
                if (metadataFuture.isCancelled()) {
                    streamFuture.cancel(true);
                }
            });
            streamFuture.whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    metadataFuture.completeExceptionally(new ModelProviderException(
                            "Failed to stream from Bedrock.",
                            unwrapCompletionException(throwable)
                    ));
                }
            });
            return metadataFuture;
        } catch (RuntimeException ex) {
            throw ex;
        }
    }

    private ConverseRequest buildConverseRequest(List<Message> messages, List<SystemContentBlock> system, String modelId) {
        return ConverseRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .system(system)
                .build();
    }

    private ConverseStreamRequest buildConverseStreamRequest(List<Message> messages, List<SystemContentBlock> system, String modelId) {
        return ConverseStreamRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .system(system)
                .build();
    }

    private List<Message> buildUserMessage(String prompt) {
        return List.of(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(prompt))
                .build());
    }

    private StructuredBedrockPrompt toStructuredPrompt(List<ProviderPromptMessage> providerMessages) {
        List<Message> messages = new ArrayList<>();
        List<SystemContentBlock> system = new ArrayList<>();

        for (ProviderPromptMessage providerMessage : providerMessages) {
            if (providerMessage == null || providerMessage.content() == null || providerMessage.content().isBlank()) {
                continue;
            }

            String role = providerMessage.role() == null ? "" : providerMessage.role().trim().toLowerCase();
            switch (role) {
                case "system" -> system.add(SystemContentBlock.fromText(providerMessage.content()));
                case "user" -> messages.add(toBedrockMessage(ConversationRole.USER, providerMessage.content()));
                case "assistant" -> messages.add(toBedrockMessage(ConversationRole.ASSISTANT, providerMessage.content()));
                default -> throw new ModelProviderException("Unsupported Bedrock prompt role: " + providerMessage.role());
            }
        }

        if (messages.isEmpty()) {
            throw new ModelProviderException("Bedrock structured prompt must contain at least one user or assistant message.");
        }

        return new StructuredBedrockPrompt(List.copyOf(messages), List.copyOf(system));
    }

    private Message toBedrockMessage(ConversationRole role, String content) {
        return Message.builder()
                .role(role)
                .content(ContentBlock.fromText(content))
                .build();
    }

    private ModelProviderReply toReply(ConverseResponse response, String modelId, long startedAt) {
        if (response.output() == null || response.output().message() == null || response.output().message().content() == null) {
            throw new ModelProviderException("Bedrock response did not contain message content.");
        }
        String output = response.output().message().content().stream()
                .map(ContentBlock::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining());
        if (output.isBlank()) {
            throw new ModelProviderException("Bedrock response did not contain text output.");
        }
        ModelProviderMetadata metadata = new ModelProviderMetadata(
                "bedrock",
                modelId,
                response.stopReason() != null ? response.stopReasonAsString() : null,
                response.usage() != null ? response.usage().inputTokens() : null,
                response.usage() != null ? response.usage().outputTokens() : null,
                response.usage() != null ? response.usage().totalTokens() : null,
                System.currentTimeMillis() - startedAt,
                response.metrics() != null ? response.metrics().latencyMs() : null,
                null,
                null
        );
        return new ModelProviderReply(output, metadata);
    }

    private record StructuredBedrockPrompt(
            List<Message> messages,
            List<SystemContentBlock> system
    ) {
    }

    private void forwardChunk(ContentBlockDeltaEvent event, Consumer<String> chunkConsumer) {
        if (event.delta() == null || event.delta().text() == null || event.delta().text().isBlank()) {
            return;
        }
        chunkConsumer.accept(event.delta().text());
    }

    private void captureStopReason(MessageStopEvent event, AtomicReference<String> stopReason) {
        if (event.stopReason() != null) {
            stopReason.set(event.stopReasonAsString());
        }
    }

    private void captureMetadata(
            ConverseStreamMetadataEvent event,
            AtomicReference<TokenUsage> usage,
            AtomicReference<ConverseStreamMetrics> metrics
    ) {
        if (event.usage() != null) {
            usage.set(event.usage());
        }
        if (event.metrics() != null) {
            metrics.set(event.metrics());
        }
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
