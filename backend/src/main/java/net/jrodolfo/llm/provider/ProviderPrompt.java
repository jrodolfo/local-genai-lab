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

    public static ProviderPrompt forPrompt(String prompt) {
        return new ProviderPrompt(prompt, List.of());
    }

    public static ProviderPrompt forMessages(List<ProviderPromptMessage> messages, String fallbackPrompt) {
        return new ProviderPrompt(fallbackPrompt, messages);
    }

    public boolean hasMessages() {
        return !messages.isEmpty();
    }
}
