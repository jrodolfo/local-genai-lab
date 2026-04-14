package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import net.jrodolfo.llm.dto.ReadReportSummaryToolRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.model.PendingToolCall;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ProviderPrompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.nio.file.Path;

@Service
public class ChatOrchestratorService {

    private final ChatModelProvider chatModelProvider;
    private final McpService mcpService;
    private final ToolDecisionService toolDecisionService;
    private final ChatMemoryService chatMemoryService;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatSessionService chatSessionService;
    private final Path reportsDirectory;

    public ChatOrchestratorService(
            ChatModelProvider chatModelProvider,
            McpService mcpService,
            ToolDecisionService toolDecisionService,
            ChatMemoryService chatMemoryService,
            ChatPromptBuilder chatPromptBuilder,
            ChatSessionService chatSessionService,
            AppStorageProperties appStorageProperties
    ) {
        this.chatModelProvider = chatModelProvider;
        this.mcpService = mcpService;
        this.toolDecisionService = toolDecisionService;
        this.chatMemoryService = chatMemoryService;
        this.chatPromptBuilder = chatPromptBuilder;
        this.chatSessionService = chatSessionService;
        this.reportsDirectory = appStorageProperties.resolvedReportsDirectory().toAbsolutePath().normalize();
    }

    public ChatResponse chat(String message, String model, String sessionId) {
        PreparedChat preparedChat = prepareChat(message, model, sessionId);
        if (preparedChat.immediateResponse() != null) {
            return preparedChat.immediateResponse();
        }
        ChatResponse response = chatModelProvider.chat(
                preparedChat.prompt(),
                preparedChat.model(),
                preparedChat.toolMetadata(),
                preparedChat.toolResult(),
                preparedChat.session().sessionId(),
                preparedChat.pendingTool()
        );
        chatMemoryService.finishTurn(
                preparedChat.session(),
                response.response(),
                response.tool(),
                response.toolResult(),
                response.metadata(),
                preparedChat.session().pendingToolCall()
        );
        return response;
    }

    public PreparedChat prepareChat(String message, String model, String sessionId) {
        String resolvedModel = chatModelProvider.resolveModel(model);
        ChatSession session = chatMemoryService.startTurn(sessionId, model, resolvedModel, message);
        ChatToolRouterService.ToolDecision routedDecision = toolDecisionService.route(message, resolvedModel);
        ChatToolRouterService.ToolDecision decision = resolveDecision(session, message, resolvedModel, routedDecision);
        if (decision.needsClarification()) {
            ChatToolMetadata metadata = new ChatToolMetadata(
                    true,
                    toolNameForDecision(decision),
                    "clarification-needed",
                    decision.clarification()
            );
            PendingToolCall pendingToolCall = pendingToolCallForDecision(decision);
            ChatSession persistedSession = chatMemoryService.finishTurn(session, decision.clarification(), metadata, null, null, pendingToolCall);
            return PreparedChat.forImmediateResponse(new ChatResponse(
                    decision.clarification(),
                    resolvedModel,
                    metadata,
                    null,
                    persistedSession.sessionId(),
                    toPendingToolResponse(pendingToolCall),
                    null
            ));
        }

        if (!decision.shouldUseTool()) {
            ChatSession clearedSession = session.withPendingToolCall(null);
            return PreparedChat.forPrompt(buildConversationPrompt(clearedSession, message, null), resolvedModel, null, null, clearedSession);
        }

        try {
            ToolExecution execution = executeTool(decision);
            ChatSession clearedSession = session.withPendingToolCall(null);
            ProviderPrompt augmentedPrompt = buildConversationPrompt(
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
            return PreparedChat.forPrompt(augmentedPrompt, resolvedModel, metadata, execution.toolResult(reportsDirectory), clearedSession);
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
                null,
                null,
                null
            );
            ChatResponse fallbackResponse = new ChatResponse(
                    buildFailureMessage(decision, ex.getMessage()),
                    resolvedModel,
                    metadata,
                    null,
                    persistedSession.sessionId(),
                    null,
                    null
            );
            return PreparedChat.forImmediateResponse(fallbackResponse);
        }
    }

    public ChatSession completePreparedChat(
            PreparedChat preparedChat,
            String assistantResponse,
            net.jrodolfo.llm.dto.ModelProviderMetadata providerMetadata
    ) {
        if (preparedChat.immediateResponse() != null || preparedChat.session() == null) {
            throw new IllegalArgumentException("Only prompt-based prepared chats can be completed.");
        }
        return chatMemoryService.finishTurn(
                preparedChat.session(),
                assistantResponse,
                preparedChat.toolMetadata(),
                preparedChat.toolResult(),
                providerMetadata,
                preparedChat.session().pendingToolCall()
        );
    }

    private ChatToolRouterService.ToolDecision resolveDecision(
            ChatSession session,
            String message,
            String model,
            ChatToolRouterService.ToolDecision routedDecision
    ) {
        if (session.pendingToolCall() == null) {
            return routedDecision;
        }

        ChatToolRouterService.ToolDecision pendingDecision = toolDecisionService.resolvePending(session.pendingToolCall(), message, model);
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

    private ProviderPrompt buildConversationPrompt(ChatSession session, String currentUserMessage, ChatPromptBuilder.ToolContext toolContext) {
        List<net.jrodolfo.llm.model.ChatSessionMessage> history = chatMemoryService.historyBeforeLatestUserMessage(session);
        if (toolContext == null) {
            return chatPromptBuilder.buildPlainChatProviderPrompt(currentUserMessage, history);
        }
        return ProviderPrompt.forPrompt(chatPromptBuilder.buildToolAssistedPrompt(currentUserMessage, history, toolContext));
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
        Map<String, Object> toolResult(Path reportsDirectory) {
            return structuredToolResult(toolName, result, reportsDirectory);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structuredToolResult(String toolName, Map<String, Object> result, Path reportsDirectory) {
        return switch (toolName) {
            case "list_recent_reports" -> Map.of(
                    "type", "report_list",
                    "reportType", String.valueOf(result.getOrDefault("report_type", "all")),
                    "reports", relativizeReports(result.getOrDefault("reports", List.of()), reportsDirectory)
            );
            case "read_report_summary" -> Map.of(
                    "type", "report_summary",
                    "reportType", String.valueOf(result.getOrDefault("report_type", "report")),
                    "runDir", artifactRelativePath(result.getOrDefault("run_dir", ""), reportsDirectory),
                    "summaryPath", artifactPath(result.get("run_dir"), "summary.json", reportsDirectory),
                    "reportPath", artifactPath(result.get("run_dir"), "report.txt", reportsDirectory),
                    "reportPreview", String.valueOf(result.getOrDefault("report_preview", "")),
                    "summary", result.getOrDefault("summary", Map.of())
            );
            case "aws_region_audit" -> {
                Map<String, Object> summary = result.get("summary") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                yield Map.ofEntries(
                        Map.entry("type", "audit_summary"),
                        Map.entry("reportType", String.valueOf(result.getOrDefault("report_type", "audit"))),
                        Map.entry("runDir", artifactRelativePath(result.getOrDefault("run_dir", ""), reportsDirectory)),
                        Map.entry("summaryPath", artifactPath(result.get("run_dir"), "summary.json", reportsDirectory)),
                        Map.entry("reportPath", artifactPath(result.get("run_dir"), "report.txt", reportsDirectory)),
                        Map.entry("accountId", String.valueOf(result.getOrDefault("account_id", ""))),
                        Map.entry("selectedRegions", result.getOrDefault("selected_regions", List.of())),
                        Map.entry("selectedServices", result.getOrDefault("selected_services", List.of())),
                        Map.entry("successCount", summary.getOrDefault("success_count", 0)),
                        Map.entry("failureCount", summary.getOrDefault("failure_count", 0)),
                        Map.entry("skippedCount", summary.getOrDefault("skipped_count", 0)),
                        Map.entry("failedSteps", relativizeFailedSteps(result.getOrDefault("failed_steps", List.of()), reportsDirectory))
                );
            }
            case "s3_cloudwatch_report" -> {
                Map<String, Object> summary = result.get("summary") instanceof Map<?, ?> map
                        ? (Map<String, Object>) map
                        : Map.of();
                yield Map.of(
                        "type", "s3_report_summary",
                        "reportType", String.valueOf(result.getOrDefault("report_type", "s3_cloudwatch")),
                        "bucket", String.valueOf(summary.getOrDefault("bucket", result.getOrDefault("bucket", ""))),
                        "runDir", artifactRelativePath(result.getOrDefault("run_dir", ""), reportsDirectory),
                        "summaryPath", artifactPath(result.get("run_dir"), "summary.json", reportsDirectory),
                        "reportPath", artifactPath(result.get("run_dir"), "report.txt", reportsDirectory),
                        "successCount", summary.getOrDefault("success_count", 0),
                        "failureCount", summary.getOrDefault("failure_count", 0),
                        "skippedCount", summary.getOrDefault("skipped_count", 0)
                );
            }
            default -> null;
        };
    }

    private static String artifactPath(Object runDir, String fileName, Path reportsDirectory) {
        if (!(runDir instanceof String runDirValue) || runDirValue.isBlank()) {
            return "";
        }
        return artifactRelativePath(Path.of(runDirValue, fileName).toString(), reportsDirectory);
    }

    @SuppressWarnings("unchecked")
    private static Object relativizeReports(Object reportsValue, Path reportsDirectory) {
        if (!(reportsValue instanceof List<?> reports)) {
            return List.of();
        }
        return reports.stream()
                .map(report -> {
                    if (!(report instanceof Map<?, ?> map)) {
                        return report;
                    }
                    Map<String, Object> reportMap = (Map<String, Object>) map;
                    return reportMap.entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> "run_dir".equals(entry.getKey())
                                            ? artifactRelativePath(entry.getValue(), reportsDirectory)
                                            : entry.getValue()
                            ));
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Object relativizeFailedSteps(Object failedStepsValue, Path reportsDirectory) {
        if (!(failedStepsValue instanceof List<?> failedSteps)) {
            return List.of();
        }
        return failedSteps.stream()
                .map(step -> {
                    if (!(step instanceof Map<?, ?> map)) {
                        return step;
                    }
                    Map<String, Object> stepMap = (Map<String, Object>) map;
                    return stepMap.entrySet().stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey,
                                    entry -> "stderr_path".equals(entry.getKey())
                                            ? artifactRelativePath(entry.getValue(), reportsDirectory)
                                            : entry.getValue()
                            ));
                })
                .toList();
    }

    private static String artifactRelativePath(Object pathValue, Path reportsDirectory) {
        if (!(pathValue instanceof String rawPath) || rawPath.isBlank()) {
            return "";
        }
        Path candidate = Path.of(rawPath);
        if (!candidate.isAbsolute()) {
            return candidate.normalize().toString();
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(reportsDirectory)) {
            return "";
        }
        return reportsDirectory.relativize(normalized).toString();
    }

    public record PreparedChat(
            ProviderPrompt prompt,
            String model,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            PendingToolCallResponse pendingTool,
            ChatSession session,
            ChatResponse immediateResponse
    ) {
        static PreparedChat forPrompt(
                ProviderPrompt prompt,
                String model,
                ChatToolMetadata toolMetadata,
                Map<String, Object> toolResult,
                ChatSession session
        ) {
            return new PreparedChat(prompt, model, toolMetadata, toolResult, null, session, null);
        }

        static PreparedChat forImmediateResponse(ChatResponse response) {
            return new PreparedChat(null, response.model(), response.tool(), response.toolResult(), response.pendingTool(), null, response);
        }
    }
}
