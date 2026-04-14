package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;

import java.util.Map;
import java.util.function.Consumer;

public interface ChatModelProvider {

    ChatResponse chat(
            ProviderPrompt prompt,
            String model,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            String sessionId,
            PendingToolCallResponse pendingTool
    );

    StreamingChatResult streamChat(ProviderPrompt prompt, String model, Consumer<String> tokenConsumer);

    String resolveModel(String model);
}
