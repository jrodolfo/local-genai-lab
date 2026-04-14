package net.jrodolfo.llm.provider;

import java.util.List;

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
