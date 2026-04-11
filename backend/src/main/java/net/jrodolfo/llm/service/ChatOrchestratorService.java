package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import net.jrodolfo.llm.dto.ReadReportSummaryToolRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.model.PendingToolCall;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatOrchestratorService {

    private final OllamaService ollamaService;
    private final McpService mcpService;
    private final ChatToolRouterService toolRouterService;
    private final ChatMemoryService chatMemoryService;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatSessionService chatSessionService;

    public ChatOrchestratorService(
            OllamaService ollamaService,
            McpService mcpService,
            ChatToolRouterService toolRouterService,
            ChatMemoryService chatMemoryService,
            ChatPromptBuilder chatPromptBuilder,
            ChatSessionService chatSessionService
    ) {
        this.ollamaService = ollamaService;
        this.mcpService = mcpService;
        this.toolRouterService = toolRouterService;
        this.chatMemoryService = chatMemoryService;
        this.chatPromptBuilder = chatPromptBuilder;
        this.chatSessionService = chatSessionService;
    }

    public ChatResponse chat(String message, String model, String sessionId) {
        PreparedChat preparedChat = prepareChat(message, model, sessionId);
        if (preparedChat.immediateResponse() != null) {
            return preparedChat.immediateResponse();
        }
        ChatResponse response = ollamaService.chat(
                preparedChat.prompt(),
                preparedChat.model(),
                preparedChat.toolMetadata(),
                preparedChat.session().sessionId(),
                preparedChat.pendingTool()
        );
        chatMemoryService.finishTurn(preparedChat.session(), response.response(), response.tool());
        return response;
    }

    public PreparedChat prepareChat(String message, String model, String sessionId) {
        String resolvedModel = ollamaService.resolveModel(model);
        ChatSession session = chatMemoryService.startTurn(sessionId, model, resolvedModel, message);
        ChatToolRouterService.ToolDecision routedDecision = toolRouterService.route(message);
        ChatToolRouterService.ToolDecision decision = resolveDecision(session, message, routedDecision);
        if (decision.needsClarification()) {
            ChatToolMetadata metadata = new ChatToolMetadata(
                    true,
                    toolNameForDecision(decision),
                    "clarification-needed",
                    decision.clarification()
            );
            PendingToolCall pendingToolCall = pendingToolCallForDecision(decision);
            ChatSession persistedSession = chatMemoryService.finishTurn(session, decision.clarification(), metadata, pendingToolCall);
            return PreparedChat.forImmediateResponse(new ChatResponse(
                    decision.clarification(),
                    resolvedModel,
                    metadata,
                    persistedSession.sessionId(),
                    toPendingToolResponse(pendingToolCall)
            ));
        }

        if (!decision.shouldUseTool()) {
            ChatSession clearedSession = session.withPendingToolCall(null);
            return PreparedChat.forPrompt(buildConversationPrompt(clearedSession, message, null), resolvedModel, null, clearedSession);
        }

        try {
            ToolExecution execution = executeTool(decision);
            ChatSession clearedSession = session.withPendingToolCall(null);
            String augmentedPrompt = buildConversationPrompt(
                    clearedSession,
                    message,
                    new ChatPromptBuilder.ToolContext(
                            execution.toolName(),
                            decision.reason(),
                            execution.summary(),
                            execution.result()
                    )
            );
            ChatToolMetadata metadata = new ChatToolMetadata(true, execution.toolName(), "success", execution.summary());
            return PreparedChat.forPrompt(augmentedPrompt, resolvedModel, metadata, clearedSession);
        } catch (IllegalArgumentException | McpClientException ex) {
            ChatToolMetadata metadata = new ChatToolMetadata(
                    true,
                    toolNameForDecision(decision),
                    "failed",
                    ex.getMessage()
            );
            ChatSession persistedSession = chatMemoryService.finishTurn(
                    session,
                    buildFailureMessage(decision, ex.getMessage()),
                    metadata,
                    null
            );
            ChatResponse fallbackResponse = new ChatResponse(
                    buildFailureMessage(decision, ex.getMessage()),
                    resolvedModel,
                    metadata,
                    persistedSession.sessionId(),
                    null
            );
            return PreparedChat.forImmediateResponse(fallbackResponse);
        }
    }

    public ChatSession completePreparedChat(PreparedChat preparedChat, String assistantResponse) {
        if (preparedChat.immediateResponse() != null || preparedChat.session() == null) {
            throw new IllegalArgumentException("Only prompt-based prepared chats can be completed.");
        }
        return chatMemoryService.finishTurn(preparedChat.session(), assistantResponse, preparedChat.toolMetadata());
    }

    private ChatToolRouterService.ToolDecision resolveDecision(
            ChatSession session,
            String message,
            ChatToolRouterService.ToolDecision routedDecision
    ) {
        if (session.pendingToolCall() == null) {
            return routedDecision;
        }

        ChatToolRouterService.ToolDecision pendingDecision = toolRouterService.resolvePending(session.pendingToolCall(), message);
        if (pendingDecision.shouldUseTool()) {
            return pendingDecision;
        }
        if (pendingDecision.needsClarification() && routedDecision.type() == ChatToolRouterService.DecisionType.NONE) {
            return pendingDecision;
        }
        return routedDecision;
    }

    private PendingToolCall pendingToolCallForDecision(ChatToolRouterService.ToolDecision decision) {
        if (!decision.needsClarification()) {
            return null;
        }

        return switch (decision.type()) {
            case S3_CLOUDWATCH_REPORT -> new PendingToolCall(
                    decision.type(),
                    decision.reportType(),
                    decision.bucket(),
                    decision.region(),
                    decision.days(),
                    decision.reason(),
                    decision.services(),
                    List.of("bucket")
            );
            case READ_LATEST_REPORT -> new PendingToolCall(
                    decision.type(),
                    decision.reportType(),
                    null,
                    null,
                    null,
                    decision.reason(),
                    decision.services(),
                    List.of("reportType")
            );
            default -> null;
        };
    }

    private PendingToolCallResponse toPendingToolResponse(PendingToolCall pendingToolCall) {
        return chatSessionService.toPendingToolResponse(pendingToolCall);
    }

    private ToolExecution executeTool(ChatToolRouterService.ToolDecision decision) {
        return switch (decision.type()) {
            case LIST_REPORTS -> {
                var response = mcpService.listRecentReports(new ListReportsRequest(decision.reportType(), 5));
                yield new ToolExecution(response.tool(), response.result(), summarizeListReports(response.result()));
            }
            case READ_LATEST_REPORT -> {
                var listResponse = mcpService.listRecentReports(new ListReportsRequest(decision.reportType(), 1));
                String runDir = extractLatestRunDir(listResponse.result());
                var readResponse = mcpService.readReportSummary(new ReadReportSummaryToolRequest(runDir, 20));
                yield new ToolExecution(readResponse.tool(), readResponse.result(), summarizeReadReport(readResponse.result()));
            }
            case AWS_REGION_AUDIT -> {
                List<String> regions = decision.region() != null ? List.of(decision.region()) : null;
                List<String> services = decision.services().isEmpty() ? null : decision.services();
                var response = mcpService.runAwsRegionAudit(new AwsRegionAuditToolRequest(regions, services));
                yield new ToolExecution(response.tool(), response.result(), summarizeAudit(response.result()));
            }
            case S3_CLOUDWATCH_REPORT -> {
                if (decision.bucket() == null || decision.bucket().isBlank()) {
                    throw new IllegalArgumentException("I recognized an S3 report request, but I could not identify the bucket name.");
                }
                var response = mcpService.runS3CloudwatchReport(new S3CloudwatchReportToolRequest(
                        decision.bucket(),
                        decision.region(),
                        decision.days() != null ? decision.days() : 14
                ));
                yield new ToolExecution(response.tool(), response.result(), summarizeS3Report(response.result()));
            }
            case NONE -> throw new IllegalArgumentException("No tool matched the request.");
        };
    }

    private String buildConversationPrompt(ChatSession session, String currentUserMessage, ChatPromptBuilder.ToolContext toolContext) {
        return chatPromptBuilder.build(new ChatPromptBuilder.PromptContext(
                currentUserMessage,
                chatMemoryService.historyBeforeLatestUserMessage(session),
                toolContext
        ));
    }

    private String buildFailureMessage(ChatToolRouterService.ToolDecision decision, String errorMessage) {
        return "I tried to use the local tool `" + toolNameForDecision(decision) + "`, but it failed: " + errorMessage;
    }

    private String toolNameForDecision(ChatToolRouterService.ToolDecision decision) {
        return switch (decision.type()) {
            case LIST_REPORTS -> "list_recent_reports";
            case READ_LATEST_REPORT -> "read_report_summary";
            case AWS_REGION_AUDIT -> "aws_region_audit";
            case S3_CLOUDWATCH_REPORT -> "s3_cloudwatch_report";
            case NONE -> "none";
        };
    }

    @SuppressWarnings("unchecked")
    private String extractLatestRunDir(Map<String, Object> result) {
        List<Map<String, Object>> reports = (List<Map<String, Object>>) result.get("reports");
        if (reports == null || reports.isEmpty()) {
            throw new IllegalArgumentException("No reports were available to read.");
        }
        Object runDir = reports.getFirst().get("run_dir");
        if (!(runDir instanceof String runDirValue) || runDirValue.isBlank()) {
            throw new IllegalArgumentException("The latest report did not include a valid run directory.");
        }
        return runDirValue;
    }

    @SuppressWarnings("unchecked")
    private String summarizeListReports(Map<String, Object> result) {
        List<Map<String, Object>> reports = (List<Map<String, Object>>) result.get("reports");
        int count = reports != null ? reports.size() : 0;
        return "Found " + count + " recent report" + (count == 1 ? "" : "s") + ".";
    }

    @SuppressWarnings("unchecked")
    private String summarizeReadReport(Map<String, Object> result) {
        Map<String, Object> summary = nestedMap(result, "summary");
        String reportType = String.valueOf(result.getOrDefault("report_type", "report"));
        return "Read %s with success_count=%s and failure_count=%s.".formatted(
                reportType,
                valueOrUnknown(summary, "success_count"),
                valueOrUnknown(summary, "failure_count")
        );
    }

    @SuppressWarnings("unchecked")
    private String summarizeAudit(Map<String, Object> result) {
        Map<String, Object> summary = nestedMap(result, "summary");
        return "AWS audit completed with success_count=%s, failure_count=%s, skipped_count=%s.".formatted(
                valueOrUnknown(summary, "success_count"),
                valueOrUnknown(summary, "failure_count"),
                valueOrUnknown(summary, "skipped_count")
        );
    }

    @SuppressWarnings("unchecked")
    private String summarizeS3Report(Map<String, Object> result) {
        Map<String, Object> summary = nestedMap(result, "summary");
        return "S3 CloudWatch report for bucket %s completed with success_count=%s and failure_count=%s.".formatted(
                valueOrUnknown(summary, "bucket"),
                valueOrUnknown(summary, "success_count"),
                valueOrUnknown(summary, "failure_count")
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Object valueOrUnknown(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "unknown" : value;
    }

    private record ToolExecution(
            String toolName,
            Map<String, Object> result,
            String summary
    ) {
    }

    public record PreparedChat(
            String prompt,
            String model,
            ChatToolMetadata toolMetadata,
            PendingToolCallResponse pendingTool,
            ChatSession session,
            ChatResponse immediateResponse
    ) {
        static PreparedChat forPrompt(
                String prompt,
                String model,
                ChatToolMetadata toolMetadata,
                ChatSession session
        ) {
            return new PreparedChat(prompt, model, toolMetadata, null, session, null);
        }

        static PreparedChat forImmediateResponse(ChatResponse response) {
            return new PreparedChat(null, response.model(), response.tool(), response.pendingTool(), null, response);
        }
    }
}
