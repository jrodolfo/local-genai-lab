package net.jrodolfo.llm.provider;

/**
 * Represents a single message in a structured prompt.
 *
 * @param role the role of the message sender (e.g., "user", "assistant", "system")
 * @param content the content of the message
 */
public record ProviderPromptMessage(
        String role,
        String content
) {
}
