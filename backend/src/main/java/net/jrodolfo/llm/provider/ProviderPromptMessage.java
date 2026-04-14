package net.jrodolfo.llm.provider;

public record ProviderPromptMessage(
        String role,
        String content
) {
}
