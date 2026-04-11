package net.jrodolfo.llm.client;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    public String converse(String prompt, String modelId) {
        try {
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
            return output;
        } catch (ValidationException ex) {
            throw new ModelProviderException("Bedrock request validation failed: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new ModelProviderException("Failed to call Bedrock converse endpoint.", ex);
        }
    }

    @Override
    public void converseStream(String prompt, String modelId, Consumer<String> chunkConsumer) {
        try {
            ConverseStreamRequest request = buildConverseStreamRequest(prompt, modelId);
            CompletableFuture<Void> completion = new CompletableFuture<>();

            ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                    .subscriber(ConverseStreamResponseHandler.Visitor.builder()
                            .onContentBlockDelta(event -> forwardChunk(event, chunkConsumer))
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

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
