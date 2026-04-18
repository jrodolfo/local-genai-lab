package net.jrodolfo.llm.client;

import org.junit.jupiter.api.Test;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.provider.ProviderPromptMessage;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwsSdkBedrockRuntimeGatewayTest {

    @Test
    void converseMapsBedrockMetadata() {
        BedrockRuntimeClient syncClient = syncClient(ConverseResponse.builder()
                .stopReason(StopReason.END_TURN)
                .usage(TokenUsage.builder().inputTokens(12).outputTokens(34).totalTokens(46).build())
                .metrics(ConverseMetrics.builder().latencyMs(321L).build())
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.fromText("hello from bedrock"))
                                .build())
                        .build())
                .build());
        AwsSdkBedrockRuntimeGateway gateway = new AwsSdkBedrockRuntimeGateway(syncClient, asyncClient((request, handler) -> CompletableFuture.completedFuture(null)));

        ModelProviderReply reply = gateway.converse("prompt", "amazon.nova-lite-v1:0");

        assertEquals("hello from bedrock", reply.text());
        ModelProviderMetadata metadata = reply.metadata();
        assertEquals("bedrock", metadata.provider());
        assertEquals("amazon.nova-lite-v1:0", metadata.modelId());
        assertEquals("end_turn", metadata.stopReason());
        assertEquals(12, metadata.inputTokens());
        assertEquals(34, metadata.outputTokens());
        assertEquals(46, metadata.totalTokens());
        assertEquals(321L, metadata.providerLatencyMs());
    }

    @Test
    void converseUsesStructuredMessagesAndSystemPromptWhenProvided() {
        AtomicBoolean asserted = new AtomicBoolean(false);
        BedrockRuntimeClient syncClient = (BedrockRuntimeClient) Proxy.newProxyInstance(
                BedrockRuntimeClient.class.getClassLoader(),
                new Class<?>[]{BedrockRuntimeClient.class},
                (proxy, method, args) -> {
                    if ("converse".equals(method.getName())) {
                        var request = (software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest) args[0];
                        assertEquals(1, request.system().size());
                        assertEquals("You are helpful.", request.system().getFirst().text());
                        assertEquals(2, request.messages().size());
                        assertEquals("hello", request.messages().get(0).content().getFirst().text());
                        assertEquals("hi", request.messages().get(1).content().getFirst().text());
                        asserted.set(true);
                        return ConverseResponse.builder()
                                .stopReason(StopReason.END_TURN)
                                .output(ConverseOutput.builder()
                                        .message(Message.builder()
                                                .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.fromText("ok"))
                                                .build())
                                        .build())
                                .build();
                    }
                    return defaultValue(method.getReturnType());
                }
        );

        AwsSdkBedrockRuntimeGateway gateway = new AwsSdkBedrockRuntimeGateway(syncClient, asyncClient((request, handler) -> CompletableFuture.completedFuture(null)));

        gateway.converse(
                List.of(
                        new ProviderPromptMessage("system", "You are helpful."),
                        new ProviderPromptMessage("user", "hello"),
                        new ProviderPromptMessage("assistant", "hi")
                ),
                "amazon.nova-lite-v1:0"
        );

        assertTrue(asserted.get());
    }

    @Test
    void converseStreamUsesStructuredMessagesAndSystemPromptWhenProvided() {
        AtomicBoolean asserted = new AtomicBoolean(false);
        BedrockRuntimeAsyncClient asyncClient = asyncClient((request, handler) -> {
            assertEquals(1, request.system().size());
            assertEquals("You are helpful.", request.system().getFirst().text());
            assertEquals(2, request.messages().size());
            assertEquals("hello", request.messages().get(0).content().getFirst().text());
            assertEquals("hi", request.messages().get(1).content().getFirst().text());
            asserted.set(true);
            handler.complete();
            return CompletableFuture.completedFuture(null);
        });

        AwsSdkBedrockRuntimeGateway gateway = new AwsSdkBedrockRuntimeGateway(syncClient(), asyncClient);

        gateway.converseStream(
                List.of(
                        new ProviderPromptMessage("system", "You are helpful."),
                        new ProviderPromptMessage("user", "hello"),
                        new ProviderPromptMessage("assistant", "hi")
                ),
                "amazon.nova-lite-v1:0",
                chunk -> { }
        ).join();

        assertTrue(asserted.get());
    }

    @Test
    void forwardChunkAddsNonBlankText() throws Exception {
        AwsSdkBedrockRuntimeGateway gateway = new AwsSdkBedrockRuntimeGateway(syncClient(), asyncClient((request, handler) -> CompletableFuture.completedFuture(null)));
        List<String> chunks = new ArrayList<>();
        invokeForwardChunk(
                gateway,
                ContentBlockDeltaEvent.builder()
                        .delta(ContentBlockDelta.builder().text("hello").build())
                        .build(),
                chunks::add
        );

        assertEquals(List.of("hello"), chunks);
    }

    @Test
    void forwardChunkIgnoresBlankText() throws Exception {
        AwsSdkBedrockRuntimeGateway gateway = new AwsSdkBedrockRuntimeGateway(syncClient(), asyncClient((request, handler) -> CompletableFuture.completedFuture(null)));
        List<String> chunks = new ArrayList<>();
        invokeForwardChunk(
                gateway,
                ContentBlockDeltaEvent.builder()
                        .delta(ContentBlockDelta.builder().text(" ").build())
                        .build(),
                chunks::add
        );

        assertEquals(List.of(), chunks);
    }

    @Test
    void converseStreamWrapsAsyncClientFailures() {
        BedrockRuntimeAsyncClient asyncClient = asyncClient((request, handler) -> {
            RuntimeException error = new RuntimeException("boom");
            handler.exceptionOccurred(error);
            return CompletableFuture.failedFuture(error);
        });

        AwsSdkBedrockRuntimeGateway gateway = new AwsSdkBedrockRuntimeGateway(syncClient(), asyncClient);

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> gateway.converseStream("prompt", "amazon.nova-lite-v1:0", chunk -> {
                }).join()
        );

        assertEquals(ModelProviderException.class, exception.getCause().getClass());
        assertEquals("Failed to stream from Bedrock.", exception.getCause().getMessage());
    }

    @Test
    void converseStreamReturnsFinalMetadata() throws Exception {
        AwsSdkBedrockRuntimeGateway gateway = new AwsSdkBedrockRuntimeGateway(syncClient(), asyncClient((request, handler) -> CompletableFuture.completedFuture(null)));
        ModelProviderMetadata metadata = invokeConverseStreamWithCapturedMetadata(gateway);

        assertEquals("bedrock", metadata.provider());
        assertEquals("amazon.nova-lite-v1:0", metadata.modelId());
        assertEquals("end_turn", metadata.stopReason());
        assertEquals(7, metadata.inputTokens());
        assertEquals(8, metadata.outputTokens());
        assertEquals(15, metadata.totalTokens());
        assertEquals(210L, metadata.providerLatencyMs());
    }

    @Test
    void converseStreamCompletesSafelyWhenBedrockOmitsUsageMetadata() {
        BedrockRuntimeAsyncClient asyncClient = asyncClient((request, handler) -> {
            handler.complete();
            return CompletableFuture.completedFuture(null);
        });

        AwsSdkBedrockRuntimeGateway gateway = new AwsSdkBedrockRuntimeGateway(syncClient(), asyncClient);

        ModelProviderMetadata metadata = gateway
                .converseStream("prompt", "amazon.nova-lite-v1:0", chunk -> { })
                .join();

        assertEquals("bedrock", metadata.provider());
        assertEquals("amazon.nova-lite-v1:0", metadata.modelId());
        assertNull(metadata.stopReason());
        assertNull(metadata.inputTokens());
        assertNull(metadata.outputTokens());
        assertNull(metadata.totalTokens());
        assertNull(metadata.providerLatencyMs());
    }

    @Test
    void converseStreamCanDeliverChunkBeforeMetadataFutureCompletes() {
        CompletableFuture<Void> remoteCompletion = new CompletableFuture<>();
        AtomicBoolean consumerCalled = new AtomicBoolean(false);
        BedrockRuntimeAsyncClient asyncClient = asyncClient((request, handler) -> remoteCompletion);

        AwsSdkBedrockRuntimeGateway gateway = new AwsSdkBedrockRuntimeGateway(syncClient(), asyncClient);

        CompletableFuture<ModelProviderMetadata> metadataFuture = gateway.converseStream(
                "prompt",
                "amazon.nova-lite-v1:0",
                chunk -> consumerCalled.set(true)
        );

        try {
            invokeForwardChunk(
                    gateway,
                    ContentBlockDeltaEvent.builder()
                            .delta(ContentBlockDelta.builder().text("hello").build())
                            .build(),
                    chunk -> consumerCalled.set(true)
            );
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        assertEquals(true, consumerCalled.get());
        assertFalse(metadataFuture.isDone());

        remoteCompletion.complete(null);
    }

    private BedrockRuntimeClient syncClient() {
        return syncClient(null);
    }

    private BedrockRuntimeClient syncClient(ConverseResponse converseResponse) {
        return (BedrockRuntimeClient) Proxy.newProxyInstance(
                BedrockRuntimeClient.class.getClassLoader(),
                new Class<?>[]{BedrockRuntimeClient.class},
                (proxy, method, args) -> {
                    if ("converse".equals(method.getName())) {
                        return converseResponse;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private BedrockRuntimeAsyncClient asyncClient(ConverseStreamInvocation invocation) {
        return (BedrockRuntimeAsyncClient) Proxy.newProxyInstance(
                BedrockRuntimeAsyncClient.class.getClassLoader(),
                new Class<?>[]{BedrockRuntimeAsyncClient.class},
                (proxy, method, args) -> {
                    if ("converseStream".equals(method.getName())) {
                        return invocation.invoke(
                                (ConverseStreamRequest) args[0],
                                (ConverseStreamResponseHandler) args[1]
                        );
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType.equals(Boolean.TYPE)) {
            return false;
        }
        if (returnType.equals(Integer.TYPE)) {
            return 0;
        }
        if (returnType.equals(Long.TYPE)) {
            return 0L;
        }
        if (returnType.equals(Float.TYPE)) {
            return 0f;
        }
        if (returnType.equals(Double.TYPE)) {
            return 0d;
        }
        return null;
    }

    private void invokeForwardChunk(
            AwsSdkBedrockRuntimeGateway gateway,
            ContentBlockDeltaEvent event,
            java.util.function.Consumer<String> consumer
    ) throws Exception {
        Method method = AwsSdkBedrockRuntimeGateway.class.getDeclaredMethod(
                "forwardChunk",
                ContentBlockDeltaEvent.class,
                java.util.function.Consumer.class
        );
        method.setAccessible(true);
        method.invoke(gateway, event, consumer);
    }

    private ModelProviderMetadata invokeConverseStreamWithCapturedMetadata(AwsSdkBedrockRuntimeGateway gateway) throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> stopReason = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<TokenUsage> usage = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<ConverseStreamMetrics> metrics = new java.util.concurrent.atomic.AtomicReference<>();

        Method stopMethod = AwsSdkBedrockRuntimeGateway.class.getDeclaredMethod(
                "captureStopReason",
                MessageStopEvent.class,
                java.util.concurrent.atomic.AtomicReference.class
        );
        stopMethod.setAccessible(true);
        stopMethod.invoke(gateway, MessageStopEvent.builder().stopReason(StopReason.END_TURN).build(), stopReason);

        Method metadataMethod = AwsSdkBedrockRuntimeGateway.class.getDeclaredMethod(
                "captureMetadata",
                ConverseStreamMetadataEvent.class,
                java.util.concurrent.atomic.AtomicReference.class,
                java.util.concurrent.atomic.AtomicReference.class
        );
        metadataMethod.setAccessible(true);
        metadataMethod.invoke(
                gateway,
                ConverseStreamMetadataEvent.builder()
                        .usage(TokenUsage.builder().inputTokens(7).outputTokens(8).totalTokens(15).build())
                        .metrics(ConverseStreamMetrics.builder().latencyMs(210L).build())
                        .build(),
                usage,
                metrics
        );

        return new ModelProviderMetadata(
                "bedrock",
                "amazon.nova-lite-v1:0",
                stopReason.get(),
                usage.get().inputTokens(),
                usage.get().outputTokens(),
                usage.get().totalTokens(),
                null,
                metrics.get().latencyMs(),
                null,
                null
        );
    }

    @FunctionalInterface
    private interface ConverseStreamInvocation {
        CompletableFuture<Void> invoke(ConverseStreamRequest request, ConverseStreamResponseHandler handler);
    }
}
