package net.jrodolfo.llm.provider;

import java.util.List;

/**
 * Represents a prompt sent to a model provider.
 * Can contain either a simple prompt string or a list of structured messages.
 *
 * @param prompt the simple prompt string (used as fallback or for providers that don't support structured messages)
 * @param messages the list of structured messages (role and content)
 */
public record ProviderPrompt(
        String prompt,
        List<ProviderPromptMessage> messages
) {

    public ProviderPrompt {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    /**
     * Creates a new ProviderPrompt with a simple prompt string and no messages.
     *
     * @param prompt the simple prompt string
     * @return a new ProviderPrompt instance
     */
    public static ProviderPrompt forPrompt(String prompt) {
        return new ProviderPrompt(prompt, List.of());
    }

    /**
     * Creates a new ProviderPrompt with a list of messages and a fallback prompt string.
     *
     * @param messages the list of structured messages
     * @param fallbackPrompt the fallback prompt string
     * @return a new ProviderPrompt instance
     */
    public static ProviderPrompt forMessages(List<ProviderPromptMessage> messages, String fallbackPrompt) {
        return new ProviderPrompt(fallbackPrompt, messages);
    }

    /**
     * Checks if this prompt contains any structured messages.
     *
     * @return true if messages is not empty, false otherwise
     */
    public boolean hasMessages() {
        return !messages.isEmpty();
    }
}
