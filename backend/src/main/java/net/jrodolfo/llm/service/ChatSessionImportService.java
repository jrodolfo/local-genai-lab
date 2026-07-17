package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.dto.ChatSessionDetailResponse;
import net.jrodolfo.llm.dto.ChatSessionImportResponse;
import net.jrodolfo.llm.dto.ChatSessionMessageResponse;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.model.ChatSessionMessage;
import net.jrodolfo.llm.model.PendingToolCall;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import net.jrodolfo.llm.util.StringUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Imports JSON session exports back into local storage.
 *
 * <p>The importer validates roles, preserves supported metadata, normalizes
 * missing timestamps, and generates a new id when the imported id is invalid or
 * already exists locally.
 */
@Service
public class ChatSessionImportService {

    private final ObjectMapper objectMapper;
    private final FileChatSessionStore sessionStore;
    private final ChatSessionMetadataService chatSessionMetadataService;
    private final SessionIdPolicy sessionIdPolicy;

    /**
     * Constructs a new ChatSessionImportService.
     *
     * @param objectMapper               the object mapper for JSON parsing
     * @param sessionStore               the store for chat sessions
     * @param chatSessionMetadataService the service for session metadata
     * @param sessionIdPolicy            the policy for session IDs
     */
    public ChatSessionImportService(
            ObjectMapper objectMapper,
            FileChatSessionStore sessionStore,
            ChatSessionMetadataService chatSessionMetadataService,
            SessionIdPolicy sessionIdPolicy
    ) {
        this.objectMapper = objectMapper;
        this.sessionStore = sessionStore;
        this.chatSessionMetadataService = chatSessionMetadataService;
        this.sessionIdPolicy = sessionIdPolicy;
    }

    /**
     * Imports a session from a JSON multipart upload.
     *
     * @param file JSON export produced by this app
     * @return import summary including the stored id and message count
     * @throws ChatSessionImportException when the file is empty, invalid, or unsupported
     */
    public ChatSessionImportResponse importSession(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ChatSessionImportException("Import file must not be empty.");
        }

        ChatSessionDetailResponse imported = parseImport(file);
        Instant now = Instant.now();
        String requestedSessionId = normalizeImportedSessionId(imported.sessionId());
        boolean idChanged = !requestedSessionId.equals(imported.sessionId()) || sessionStore.findById(requestedSessionId).isPresent();
        String storedSessionId = requestedSessionId;
        while (sessionStore.findById(storedSessionId).isPresent()) {
            storedSessionId = UUID.randomUUID().toString();
        }

        List<ChatSessionMessage> messages = normalizeMessages(imported.messages(), now);
        if (messages.isEmpty()) {
            throw new ChatSessionImportException("Imported session must contain at least one message.");
        }

        Instant createdAt = imported.createdAt() != null ? imported.createdAt() : messages.getFirst().timestamp();
        Instant updatedAt = imported.updatedAt() != null ? imported.updatedAt() : messages.getLast().timestamp();
        String model = StringUtils.hasText(imported.model()) ? imported.model().trim() : "llama3:8b";

        ChatSession session = new ChatSession(
                storedSessionId,
                model,
                createdAt,
                updatedAt,
                messages,
                toPendingToolCall(imported.pendingTool()),
                imported.title(),
                imported.summary(),
                StringUtils.hasText(imported.mode()) ? imported.mode().trim() : "chat"
        );

        ChatSession saved = sessionStore.save(chatSessionMetadataService.enrich(session));
        return new ChatSessionImportResponse(
                saved.sessionId(),
                chatSessionMetadataService.fallbackTitle(saved),
                chatSessionMetadataService.fallbackSummary(saved),
                idChanged,
                saved.messages().size()
        );
    }

    /**
     * Normalizes an imported session ID, generating a new one if necessary.
     *
     * @param importedSessionId the original session ID from the import
     * @return a valid session ID
     */
    private String normalizeImportedSessionId(String importedSessionId) {
        if (!StringUtils.hasText(importedSessionId)) {
            return UUID.randomUUID().toString();
        }
        if (!sessionIdPolicy.isValid(importedSessionId)) {
            return UUID.randomUUID().toString();
        }
        return sessionIdPolicy.requireValid(importedSessionId);
    }

    /**
     * Parses the import file into a ChatSessionDetailResponse DTO.
     *
     * @param file the multipart file
     * @return the parsed session detail response
     */
    private ChatSessionDetailResponse parseImport(MultipartFile file) {
        try {
            return objectMapper.readValue(file.getInputStream(), ChatSessionDetailResponse.class);
        } catch (IOException ex) {
            throw new ChatSessionImportException("Import file is not valid JSON.", ex);
        }
    }

    /**
     * Normalizes a list of imported message responses into internal session messages.
     *
     * @param importedMessages the list of imported messages
     * @param fallbackTime     the fallback timestamp to use
     * @return a list of normalized session messages
     */
    private List<ChatSessionMessage> normalizeMessages(List<ChatSessionMessageResponse> importedMessages, Instant fallbackTime) {
        if (importedMessages == null) {
            return List.of();
        }

        List<ChatSessionMessage> normalized = new ArrayList<>();
        Instant lastTimestamp = fallbackTime;

        for (ChatSessionMessageResponse message : importedMessages) {
            if (message == null) {
                throw new ChatSessionImportException("Imported session contains an invalid message entry.");
            }

            String role = normalizeRole(message.role());
            String content = message.content();
            if (content == null) {
                throw new ChatSessionImportException("Imported session message content must not be null.");
            }

            Instant timestamp = message.timestamp() != null ? message.timestamp() : lastTimestamp;
            lastTimestamp = timestamp;
            normalized.add(new ChatSessionMessage(role, content, message.tool(), message.toolResult(), message.metadata(), message.ragSources(), message.ragRetrieval(), message.ragTiming(), timestamp));
        }

        return normalized;
    }

    /**
     * Converts an imported pending tool call response to an internal PendingToolCall object.
     *
     * @param pendingTool the pending tool call response
     * @return the internal pending tool call object
     */
    private PendingToolCall toPendingToolCall(net.jrodolfo.llm.dto.PendingToolCallResponse pendingTool) {
        if (pendingTool == null) {
            return null;
        }

        return new PendingToolCall(
                switch (normalizeToolName(pendingTool.toolName())) {
                    case "list_recent_reports" -> ChatToolRouterService.DecisionType.LIST_REPORTS;
                    case "read_report_summary" -> ChatToolRouterService.DecisionType.READ_LATEST_REPORT;
                    case "aws_region_audit" -> ChatToolRouterService.DecisionType.AWS_REGION_AUDIT;
                    case "s3_cloudwatch_report" -> ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT;
                    default ->
                            throw new ChatSessionImportException("Unsupported pending tool: " + pendingTool.toolName());
                },
                pendingTool.reportType(),
                pendingTool.bucket(),
                pendingTool.region(),
                pendingTool.days(),
                pendingTool.reason(),
                pendingTool.services() == null ? List.of() : pendingTool.services(),
                pendingTool.bucketOptions() == null ? List.of() : pendingTool.bucketOptions(),
                pendingTool.missingFields() == null ? List.of() : pendingTool.missingFields()
        );
    }

    /**
     * Normalizes the role name, ensuring it is either "user" or "assistant".
     *
     * @param role the raw role name
     * @return the normalized role name
     */
    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new ChatSessionImportException("Imported session message role must not be blank.");
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("user") && !normalized.equals("assistant")) {
            throw new ChatSessionImportException("Unsupported imported message role: " + role);
        }
        return normalized;
    }

    /**
     * Normalizes a tool name by trimming whitespace.
     *
     * @param toolName the tool name
     * @return the normalized tool name
     */
    private String normalizeToolName(String toolName) {
        return StringUtils.hasText(toolName) ? toolName.trim() : "";
    }

}
