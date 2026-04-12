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

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ChatSessionImportService {

    private final ObjectMapper objectMapper;
    private final FileChatSessionStore sessionStore;
    private final ChatSessionMetadataService chatSessionMetadataService;
    private final SessionIdPolicy sessionIdPolicy;

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
        String model = hasText(imported.model()) ? imported.model().trim() : "llama3:8b";

        ChatSession session = new ChatSession(
                storedSessionId,
                model,
                createdAt,
                updatedAt,
                messages,
                toPendingToolCall(imported.pendingTool()),
                imported.title(),
                imported.summary()
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

    private String normalizeImportedSessionId(String importedSessionId) {
        if (!hasText(importedSessionId)) {
            return UUID.randomUUID().toString();
        }
        if (!sessionIdPolicy.isValid(importedSessionId)) {
            return UUID.randomUUID().toString();
        }
        return sessionIdPolicy.requireValid(importedSessionId);
    }

    private ChatSessionDetailResponse parseImport(MultipartFile file) {
        try {
            return objectMapper.readValue(file.getInputStream(), ChatSessionDetailResponse.class);
        } catch (IOException ex) {
            throw new ChatSessionImportException("Import file is not valid JSON.", ex);
        }
    }

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
            normalized.add(new ChatSessionMessage(role, content, message.tool(), message.toolResult(), message.metadata(), timestamp));
        }

        return normalized;
    }

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
                    default -> throw new ChatSessionImportException("Unsupported pending tool: " + pendingTool.toolName());
                },
                null,
                null,
                null,
                null,
                pendingTool.reason(),
                List.of(),
                pendingTool.missingFields() == null ? List.of() : pendingTool.missingFields()
        );
    }

    private String normalizeRole(String role) {
        if (!hasText(role)) {
            throw new ChatSessionImportException("Imported session message role must not be blank.");
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("user") && !normalized.equals("assistant")) {
            throw new ChatSessionImportException("Unsupported imported message role: " + role);
        }
        return normalized;
    }

    private String normalizeToolName(String toolName) {
        return hasText(toolName) ? toolName.trim() : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
