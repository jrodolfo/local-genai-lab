package net.jrodolfo.llm.service;

import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.model.ChatSessionMessage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing chat session metadata, such as titles and summaries.
 */
@Service
public class ChatSessionMetadataService {

    private static final int TITLE_MAX_LENGTH = 60;
    private static final int SUMMARY_MAX_LENGTH = 120;

    /**
     * Enriches a chat session with a generated title and summary if they are missing.
     *
     * @param session the chat session to enrich
     * @return the enriched chat session
     */
    public ChatSession enrich(ChatSession session) {
        String title = hasText(session.title()) ? session.title() : generateTitle(session.messages());
        String summary = hasText(session.summary()) ? session.summary() : generateSummary(session.messages(), title);
        return session.withMetadata(title, summary);
    }

    /**
     * Gets the title of a session or generates a fallback if it's missing.
     *
     * @param session the chat session
     * @return the title or fallback
     */
    public String fallbackTitle(ChatSession session) {
        return hasText(session.title()) ? session.title() : generateTitle(session.messages());
    }

    /**
     * Gets the summary of a session or generates a fallback if it's missing.
     *
     * @param session the chat session
     * @return the summary or fallback
     */
    public String fallbackSummary(ChatSession session) {
        return hasText(session.summary()) ? session.summary() : generateSummary(session.messages(), fallbackTitle(session));
    }

    /**
     * Generates a title based on the first user message.
     *
     * @param messages the session messages
     * @return the generated title
     */
    private String generateTitle(List<ChatSessionMessage> messages) {
        return messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ChatSessionMessage::content)
                .map(this::normalizeTitle)
                .filter(this::hasText)
                .findFirst()
                .orElse("New chat");
    }

    /**
     * Generates a summary based on the latest assistant message.
     *
     * @param messages the session messages
     * @param title    the session title
     * @return the generated summary
     */
    private String generateSummary(List<ChatSessionMessage> messages, String title) {
        if (messages == null || messages.isEmpty()) {
            return title;
        }

        String latestAssistant = messages.stream()
                .filter(message -> "assistant".equals(message.role()))
                .reduce((first, second) -> second)
                .map(ChatSessionMessage::content)
                .map(this::normalizeSummary)
                .filter(this::hasText)
                .orElse(null);

        if (latestAssistant != null) {
            return latestAssistant;
        }

        return title;
    }

    /**
     * Normalizes and truncates a string for use as a title.
     *
     * @param content the raw title content
     * @return the normalized and truncated title
     */
    private String normalizeTitle(String content) {
        String normalized = normalizeWhitespace(content);
        if (!hasText(normalized)) {
            return "New chat";
        }
        if (normalized.length() <= TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, TITLE_MAX_LENGTH - 1) + "…";
    }

    /**
     * Normalizes and truncates a string for use as a summary.
     *
     * @param content the raw summary content
     * @return the normalized and truncated summary
     */
    private String normalizeSummary(String content) {
        String normalized = normalizeWhitespace(content);
        if (!hasText(normalized)) {
            return "";
        }
        if (normalized.length() <= SUMMARY_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SUMMARY_MAX_LENGTH - 1) + "…";
    }

    /**
     * Normalizes whitespace in a string by trimming and replacing multiple spaces with a single space.
     *
     * @param content the raw string content
     * @return the normalized string
     */
    private String normalizeWhitespace(String content) {
        return content == null ? "" : content.trim().replaceAll("\\s+", " ");
    }

    /**
     * Checks if a string has text (not null and not blank).
     *
     * @param value the string value
     * @return true if it has text, false otherwise
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
