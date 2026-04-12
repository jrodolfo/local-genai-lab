package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class OllamaChatModelProvider implements ChatModelProvider {

    private final OllamaClient ollamaClient;

    public OllamaChatModelProvider(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    @Override
    public ChatResponse chat(
            String message,
            String model,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            String sessionId,
            PendingToolCallResponse pendingTool
    ) {
        String normalizedMessage = message.trim();
        String resolvedModel = ollamaClient.resolveModel(model);
        String response = ollamaClient.generate(normalizedMessage, resolvedModel);
        return new ChatResponse(
                response,
                resolvedModel,
                toolMetadata,
                toolResult,
                sessionId,
                pendingTool,
                new ModelProviderMetadata("ollama", resolvedModel, null, null, null, null, null, null)
        );
    }

    @Override
    public StreamingChatResult streamChat(String message, String model, Consumer<String> tokenConsumer) {
        String normalizedMessage = message.trim();
        String resolvedModel = ollamaClient.resolveModel(model);
        ollamaClient.streamGenerate(normalizedMessage, resolvedModel, tokenConsumer);
        return new StreamingChatResult(CompletableFuture.completedFuture(
                new ModelProviderMetadata("ollama", resolvedModel, null, null, null, null, null, null)
        ));
    }

    @Override
    public String resolveModel(String model) {
        return ollamaClient.resolveModel(model);
    }
}
