package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;

import java.util.function.Consumer;

public interface ChatModelProvider {

    ChatResponse chat(
            String message,
            String model,
            ChatToolMetadata toolMetadata,
            String sessionId,
            PendingToolCallResponse pendingTool
    );

    ModelProviderMetadata streamChat(String message, String model, Consumer<String> tokenConsumer);

    String resolveModel(String model);
}
