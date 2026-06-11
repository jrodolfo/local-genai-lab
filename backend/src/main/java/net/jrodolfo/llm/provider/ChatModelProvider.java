package net.jrodolfo.llm.provider;

import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Common contract implemented by every chat model provider.
 *
 * <p>Provider implementations adapt a normalized backend prompt to a concrete
 * runtime such as Ollama, Bedrock, or Hugging Face. The orchestration layer owns
 * tools and persistence; providers own model resolution, request execution, and
 * provider-specific metadata.
 */
public interface ChatModelProvider {

    /**
     * Executes a synchronous chat request.
     *
     * @param prompt       the prompt containing the user input or message history
     * @param model        the model identifier to use (optional)
     * @param toolMetadata metadata about available tools (optional)
     * @param toolResult   results from previously executed tools (optional)
     * @param sessionId    the session identifier for tracking the conversation
     * @param pendingTool  information about a tool call that is pending completion (optional)
     * @return the chat response containing the generated text and metadata
     */
    ChatResponse chat(
            ProviderPrompt prompt,
            String model,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            String sessionId,
            PendingToolCallResponse pendingTool
    );

    /**
     * Executes a streaming chat request.
     *
     * @param prompt        the prompt containing the user input or message history
     * @param model         the model identifier to use (optional)
     * @param tokenConsumer a consumer that will receive generated tokens as they arrive
     * @return a result object containing the completion future and a cancellation handle
     */
    StreamingChatResult streamChat(ProviderPrompt prompt, String model, Consumer<String> tokenConsumer);

    /**
     * Resolves the model identifier, potentially using a default if none is provided.
     *
     * @param model the requested model identifier
     * @return the resolved model identifier
     */
    String resolveModel(String model);
}
