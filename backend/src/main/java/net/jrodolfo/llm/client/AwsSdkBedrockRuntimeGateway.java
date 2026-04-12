package net.jrodolfo.llm.client;

import net.jrodolfo.llm.dto.ModelProviderMetadata;
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
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
            ConverseRequest request = buildConverseRequest(prompt, modelId);
            ConverseResponse response = bedrockRuntimeClient.converse(request);
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
                    response.metrics() != null ? response.metrics().latencyMs() : null
            );
            return new ModelProviderReply(output, metadata);
        } catch (ValidationException ex) {
            throw new ModelProviderException("Bedrock request validation failed: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new ModelProviderException("Failed to call Bedrock converse endpoint.", ex);
        }
    }

    @Override
    public ModelProviderMetadata converseStream(String prompt, String modelId, Consumer<String> chunkConsumer) {
        try {
            ConverseStreamRequest request = buildConverseStreamRequest(prompt, modelId);
            CompletableFuture<Void> completion = new CompletableFuture<>();
            long startedAt = System.currentTimeMillis();
            AtomicReference<String> stopReason = new AtomicReference<>();
            AtomicReference<TokenUsage> usage = new AtomicReference<>();
            AtomicReference<ConverseStreamMetrics> metrics = new AtomicReference<>();

            ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                    .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                            .onContentBlockDelta(event -> forwardChunk(event, chunkConsumer))
                            .onMessageStop(event -> captureStopReason(event, stopReason))
                            .onMetadata(event -> captureMetadata(event, usage, metrics))
                            .build())
                    .onError(error -> completion.completeExceptionally(
                            new ModelProviderException("Failed to stream from Bedrock.", error)
                    ))
                    .onComplete(() -> completion.complete(null))
                    .build();

            CompletableFuture<Void> streamFuture = bedrockRuntimeAsyncClient.converseStream(request, handler);
            streamFuture.whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    completion.completeExceptionally(new ModelProviderException(
                            "Failed to stream from Bedrock.",
                            unwrapCompletionException(throwable)
                    ));
                }
            });

            completion.join();
            TokenUsage finalUsage = usage.get();
            ConverseStreamMetrics finalMetrics = metrics.get();
            return new ModelProviderMetadata(
                    "bedrock",
                    modelId,
                    stopReason.get(),
                    finalUsage != null ? finalUsage.inputTokens() : null,
                    finalUsage != null ? finalUsage.outputTokens() : null,
                    finalUsage != null ? finalUsage.totalTokens() : null,
                    System.currentTimeMillis() - startedAt,
                    finalMetrics != null ? finalMetrics.latencyMs() : null
            );
        } catch (ValidationException ex) {
            throw new ModelProviderException("Bedrock request validation failed: " + ex.getMessage(), ex);
        } catch (CompletionException ex) {
            Throwable cause = unwrapCompletionException(ex);
            if (cause instanceof ModelProviderException modelProviderException) {
                throw modelProviderException;
            }
            throw new ModelProviderException("Failed to stream from Bedrock.", cause);
        } catch (SdkException ex) {
            throw new ModelProviderException("Failed to stream from Bedrock.", ex);
        } catch (RuntimeException ex) {
            throw new ModelProviderException("Failed to stream from Bedrock.", ex);
        }
    }

    private ConverseRequest buildConverseRequest(String prompt, String modelId) {
        return ConverseRequest.builder()
                .modelId(modelId)
                .messages(buildUserMessage(prompt))
                .build();
    }

    private ConverseStreamRequest buildConverseStreamRequest(String prompt, String modelId) {
        return ConverseStreamRequest.builder()
                .modelId(modelId)
                .messages(buildUserMessage(prompt))
                .build();
    }

    private Message buildUserMessage(String prompt) {
        return Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(prompt))
                .build();
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
