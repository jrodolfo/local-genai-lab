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
            ProviderPrompt prompt,
            String model,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            String sessionId,
            PendingToolCallResponse pendingTool
    ) {
        String resolvedModel = ollamaClient.resolveModel(model);
        String response = prompt.hasMessages()
                ? ollamaClient.chat(prompt.messages(), resolvedModel)
                : ollamaClient.generate(prompt.prompt().trim(), resolvedModel);
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
    public StreamingChatResult streamChat(ProviderPrompt prompt, String model, Consumer<String> tokenConsumer) {
        String resolvedModel = ollamaClient.resolveModel(model);
        if (prompt.hasMessages()) {
            ollamaClient.streamChat(prompt.messages(), resolvedModel, tokenConsumer);
        } else {
            ollamaClient.streamGenerate(prompt.prompt().trim(), resolvedModel, tokenConsumer);
        }
        return new StreamingChatResult(
                CompletableFuture.completedFuture(
                        new ModelProviderMetadata("ollama", resolvedModel, null, null, null, null, null, null)
                ),
                () -> { }
        );
    }

    @Override
    public String resolveModel(String model) {
        return ollamaClient.resolveModel(model);
    }
}
