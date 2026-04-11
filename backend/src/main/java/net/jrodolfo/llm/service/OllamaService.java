package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.OllamaClient;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class OllamaService {

    private final OllamaClient ollamaClient;

    public OllamaService(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }

    public ChatResponse chat(String message, String model) {
        return chat(message, model, null, null, null);
    }

    public ChatResponse chat(String message, String model, ChatToolMetadata toolMetadata) {
        return chat(message, model, toolMetadata, null, null);
    }

    public ChatResponse chat(String message, String model, ChatToolMetadata toolMetadata, String sessionId) {
        return chat(message, model, toolMetadata, sessionId, null);
    }

    public ChatResponse chat(
            String message,
            String model,
            ChatToolMetadata toolMetadata,
            String sessionId,
            PendingToolCallResponse pendingTool
    ) {
        String normalizedMessage = message.trim();
        String resolvedModel = ollamaClient.resolveModel(model);
        String response = ollamaClient.generate(normalizedMessage, resolvedModel);
        return new ChatResponse(response, resolvedModel, toolMetadata, sessionId, pendingTool);
    }

    public void streamChat(String message, String model, Consumer<String> tokenConsumer) {
        String normalizedMessage = message.trim();
        String resolvedModel = ollamaClient.resolveModel(model);
        ollamaClient.streamGenerate(normalizedMessage, resolvedModel, tokenConsumer);
    }

    public String resolveModel(String model) {
        return ollamaClient.resolveModel(model);
    }
}
