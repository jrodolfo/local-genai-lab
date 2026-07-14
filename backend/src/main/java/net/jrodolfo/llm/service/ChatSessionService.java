package net.jrodolfo.llm.service;

import net.jrodolfo.llm.dto.ChatSessionDetailResponse;
import net.jrodolfo.llm.dto.ChatSessionMessageResponse;
import net.jrodolfo.llm.dto.ChatSessionSummaryResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.model.ChatSessionMessage;
import net.jrodolfo.llm.model.PendingToolCall;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Read/delete facade for locally persisted chat and RAG sessions.
 *
 * <p>This service turns internal {@link ChatSession} files into frontend DTOs,
 * applies sidebar filters, and hides fallback title/summary logic from
 * controllers.
 */
@Service
public class ChatSessionService {

    private final FileChatSessionStore sessionStore;
    private final ChatSessionMetadataService chatSessionMetadataService;

    /**
     * Constructs a new ChatSessionService.
     *
     * @param sessionStore               the store for chat sessions
     * @param chatSessionMetadataService the service for session metadata
     */
    public ChatSessionService(FileChatSessionStore sessionStore, ChatSessionMetadataService chatSessionMetadataService) {
        this.sessionStore = sessionStore;
        this.chatSessionMetadataService = chatSessionMetadataService;
    }

    /**
     * Lists sessions for the sidebar using optional filters.
     *
     * @param query     text matched against title, summary, and message content
     * @param provider  provider metadata filter; unknown provider values are ignored
     * @param toolUsage {@code used}, {@code unused}, or ignored for any other value
     * @param pending   true for sessions awaiting clarification, false for sessions without pending tools
     * @param mode      session mode such as {@code chat} or {@code rag}
     * @return newest matching sessions first
     */
    public List<ChatSessionSummaryResponse> listSessions(String query, String provider, String toolUsage, Boolean pending, String mode) {
        return sessionStore.findAll().stream()
                .filter(session -> matchesQuery(session, query))
                .filter(session -> matchesProvider(session, provider))
                .filter(session -> matchesToolUsage(session, toolUsage))
                .filter(session -> matchesPending(session, pending))
                .filter(session -> matchesMode(session, mode))
                .sorted(Comparator.comparing(ChatSession::updatedAt).reversed())
                .map(this::toSummary)
                .toList();
    }

    /**
     * Loads one session with full message details.
     *
     * @param sessionId session id validated by the store
     * @return full session details for restoration or export
     * @throws ChatSessionNotFoundException when no local JSON session exists for the id
     */
    public ChatSessionDetailResponse getSession(String sessionId) {
        ChatSession session = sessionStore.findById(sessionId)
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));
        return toDetail(session);
    }

    /**
     * Deletes a chat session by its ID.
     *
     * @param sessionId the session ID
     */
    public void deleteSession(String sessionId) {
        if (!sessionStore.deleteById(sessionId)) {
            throw new ChatSessionNotFoundException(sessionId);
        }
    }

    /**
     * Converts a chat session to a summary response DTO.
     *
     * @param session the chat session
     * @return the summary response DTO
     */
    private ChatSessionSummaryResponse toSummary(ChatSession session) {
        return new ChatSessionSummaryResponse(
                session.sessionId(),
                chatSessionMetadataService.fallbackTitle(session),
                chatSessionMetadataService.fallbackSummary(session),
                session.mode(),
                latestProvider(session),
                session.model(),
                session.createdAt(),
                session.updatedAt(),
                session.messages().size()
        );
    }

    /**
     * Returns the newest provider metadata value stored in assistant messages.
     *
     * @param session the chat session
     * @return the latest provider id, or null if the session predates provider metadata
     */
    private String latestProvider(ChatSession session) {
        List<ChatSessionMessage> messages = session.messages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            ModelProviderMetadata metadata = messages.get(index).metadata();
            if (metadata != null && metadata.provider() != null && !metadata.provider().isBlank()) {
                return metadata.provider();
            }
        }
        return null;
    }

    /**
     * Checks if a session matches a search query.
     *
     * @param session the chat session
     * @param query   the search query
     * @return true if it matches, false otherwise
     */
    private boolean matchesQuery(ChatSession session, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (chatSessionMetadataService.fallbackTitle(session).toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        if (chatSessionMetadataService.fallbackSummary(session).toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }

        return session.messages().stream()
                .map(ChatSessionMessage::content)
                .filter(content -> content != null)
                .map(content -> content.toLowerCase(Locale.ROOT))
                .anyMatch(content -> content.contains(normalizedQuery));
    }

    /**
     * Checks if a session matches a provider filter.
     *
     * @param session  the chat session
     * @param provider the provider name
     * @return true if it matches, false otherwise
     */
    private boolean matchesProvider(ChatSession session, String provider) {
        if (provider == null || provider.isBlank()) {
            return true;
        }

        String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);
        if (!normalizedProvider.equals("ollama")
                && !normalizedProvider.equals("bedrock")
                && !normalizedProvider.equals("huggingface")) {
            return true;
        }

        return session.messages().stream()
                .map(ChatSessionMessage::metadata)
                .filter(metadata -> metadata != null && metadata.provider() != null)
                .map(ModelProviderMetadata::provider)
                .anyMatch(value -> normalizedProvider.equalsIgnoreCase(value));
    }

    /**
     * Checks if a session matches a tool usage filter.
     *
     * @param session   the chat session
     * @param toolUsage the tool usage filter ("used" or "unused")
     * @return true if it matches, false otherwise
     */
    private boolean matchesToolUsage(ChatSession session, String toolUsage) {
        if (toolUsage == null || toolUsage.isBlank()) {
            return true;
        }

        boolean usedTool = session.messages().stream()
                .map(ChatSessionMessage::tool)
                .filter(tool -> tool != null)
                .anyMatch(ChatToolMetadata::used);

        return switch (toolUsage.trim().toLowerCase(Locale.ROOT)) {
            case "used" -> usedTool;
            case "unused" -> !usedTool;
            default -> true;
        };
    }

    /**
     * Checks if a session matches a pending tool call filter.
     *
     * @param session the chat session
     * @param pending the pending status
     * @return true if it matches, false otherwise
     */
    private boolean matchesPending(ChatSession session, Boolean pending) {
        if (pending == null) {
            return true;
        }
        return pending == (session.pendingToolCall() != null);
    }

    /**
     * Checks if a session matches a mode filter.
     *
     * @param session the chat session
     * @param mode    the mode
     * @return true if it matches, false otherwise
     */
    private boolean matchesMode(ChatSession session, String mode) {
        if (mode == null || mode.isBlank()) {
            return true;
        }
        return mode.trim().equalsIgnoreCase(session.mode());
    }

    /**
     * Converts a chat session to a detailed response DTO.
     *
     * @param session the chat session
     * @return the detailed response DTO
     */
    private ChatSessionDetailResponse toDetail(ChatSession session) {
        return new ChatSessionDetailResponse(
                session.sessionId(),
                chatSessionMetadataService.fallbackTitle(session),
                chatSessionMetadataService.fallbackSummary(session),
                session.mode(),
                session.model(),
                session.createdAt(),
                session.updatedAt(),
                session.messages().stream().map(this::toMessageResponse).toList(),
                toPendingToolResponse(session.pendingToolCall())
        );
    }

    /**
     * Converts a chat session message to a message response DTO.
     *
     * @param message the chat session message
     * @return the message response DTO
     */
    private ChatSessionMessageResponse toMessageResponse(ChatSessionMessage message) {
        return new ChatSessionMessageResponse(
                message.role(),
                message.content(),
                message.tool(),
                message.toolResult(),
                message.metadata(),
                message.ragSources(),
                message.ragRetrieval(),
                message.ragTiming(),
                message.timestamp()
        );
    }

    /**
     * Converts an internal pending tool call to a DTO response.
     *
     * @param pendingToolCall the pending tool call
     * @return the pending tool call response DTO
     */
    public PendingToolCallResponse toPendingToolResponse(PendingToolCall pendingToolCall) {
        if (pendingToolCall == null) {
            return null;
        }

        return new PendingToolCallResponse(
                toolNameForDecision(pendingToolCall.type()),
                pendingToolCall.reason(),
                pendingToolCall.missingFields(),
                pendingToolCall.reportType(),
                pendingToolCall.bucket(),
                pendingToolCall.region(),
                pendingToolCall.days(),
                pendingToolCall.services(),
                pendingToolCall.bucketOptions()
        );
    }

    /**
     * Maps a decision type to a tool name string.
     *
     * @param type the decision type
     * @return the tool name
     */
    private String toolNameForDecision(ChatToolRouterService.DecisionType type) {
        return switch (type) {
            case LIST_REPORTS -> "list_recent_reports";
            case READ_LATEST_REPORT -> "read_report_summary";
            case AWS_REGION_AUDIT -> "aws_region_audit";
            case S3_CLOUDWATCH_REPORT -> "s3_cloudwatch_report";
            case NONE -> "none";
        };
    }
}
