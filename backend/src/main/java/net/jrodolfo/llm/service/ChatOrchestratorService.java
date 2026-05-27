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
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import net.jrodolfo.llm.provider.ProviderPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.nio.file.Path;

/**
 * Coordinates the backend chat lifecycle.
 *
 * <p>This service decides whether a turn should go directly to the active model provider, request
 * more information from the user, execute an MCP-backed tool first, or return an immediate
 * fallback after tool failure.
 */
@Service
public class ChatOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestratorService.class);

    private final ChatModelProviderRegistry chatModelProviderRegistry;
    private final McpService mcpService;
    private final ToolDecisionService toolDecisionService;
    private final ChatMemoryService chatMemoryService;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatSessionService chatSessionService;
    private final Path reportsDirectory;
    private static final ToolPhaseListener NOOP_TOOL_PHASE_LISTENER = (phaseType, toolName) -> { };

    /**
     * Constructs a new ChatOrchestratorService.
     *
     * @param chatModelProviderRegistry the registry of chat model providers
     * @param mcpService the service for MCP tools
     * @param toolDecisionService the service for making tool decisions
     * @param chatMemoryService the service for managing chat memory
     * @param chatPromptBuilder the builder for chat prompts
     * @param chatSessionService the service for chat sessions
     * @param appStorageProperties application storage properties
     */
    public ChatOrchestratorService(
            ChatModelProviderRegistry chatModelProviderRegistry,
            McpService mcpService,
            ToolDecisionService toolDecisionService,
            ChatMemoryService chatMemoryService,
            ChatPromptBuilder chatPromptBuilder,
            ChatSessionService chatSessionService,
            AppStorageProperties appStorageProperties
    ) {
        this.chatModelProviderRegistry = chatModelProviderRegistry;
        this.mcpService = mcpService;
        this.toolDecisionService = toolDecisionService;
        this.chatMemoryService = chatMemoryService;
        this.chatPromptBuilder = chatPromptBuilder;
        this.chatSessionService = chatSessionService;
        this.reportsDirectory = appStorageProperties.resolvedReportsDirectory().toAbsolutePath().normalize();
    }

    /**
     * Executes a chat turn.
     *
     * @param message the user message
     * @param provider the model provider name
     * @param model the model name
     * @param sessionId the session ID
     * @return the chat response
     */
    public ChatResponse chat(String message, String provider, String model, String sessionId) {
        return chat(message, provider, model, sessionId, null);
    }

    /**
     * Executes a chat turn with a request ID.
     *
     * @param message the user message
     * @param provider the model provider name
     * @param model the model name
     * @param sessionId the session ID
     * @param requestId the request ID for logging
     * @return the chat response
     */
    public ChatResponse chat(String message, String provider, String model, String sessionId, String requestId) {
        PreparedChat preparedChat = prepareChat(message, provider, model, sessionId, requestId);
        if (preparedChat.immediateResponse() != null) {
            return preparedChat.immediateResponse();
        }
        ChatResponse response = preparedChat.provider().chat(
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

    /**
     * Prepares a chat turn before the controller chooses normal or streaming provider execution.
     *
     * <p>The result is either an immediate response for clarification/failure cases or a
     * prompt-backed continuation that can be executed later.
     */
    /**
     * Prepares a chat turn before the controller chooses normal or streaming provider execution.
     *
     * <p>The result is either an immediate response for clarification/failure cases or a
     * prompt-backed continuation that can be executed later.
     *
     * @param message the user message
     * @param provider the model provider name
     * @param model the model name
     * @param sessionId the session ID
     * @return the prepared chat object
     */
    public PreparedChat prepareChat(String message, String provider, String model, String sessionId) {
        return prepareChat(message, provider, model, sessionId, null);
    }

    /**
     * Prepares a chat turn and includes the current request id in orchestration logs.
     */
    /**
     * Prepares a chat turn and includes the current request id in orchestration logs.
     *
     * @param message the user message
     * @param provider the model provider name
     * @param model the model name
     * @param sessionId the session ID
     * @param requestId the request ID for logging
     * @return the prepared chat object
     */
    public PreparedChat prepareChat(String message, String provider, String model, String sessionId, String requestId) {
        return prepareChat(message, provider, model, sessionId, requestId, NOOP_TOOL_PHASE_LISTENER);
    }

    /**
     * Prepares a chat turn with full context and a tool phase listener.
     *
     * @param message the user message
     * @param provider the model provider name
     * @param model the model name
     * @param sessionId the session ID
     * @param requestId the request ID for logging
     * @param toolPhaseListener the listener for tool execution phases
     * @return the prepared chat object
     */
    public PreparedChat prepareChat(
            String message,
            String provider,
            String model,
            String sessionId,
            String requestId,
            ToolPhaseListener toolPhaseListener
    ) {
        toolPhaseListener.onPhase("tool-decision-started", null);
        ChatModelProvider chatModelProvider = chatModelProviderRegistry.get(provider);
        String resolvedModel = chatModelProvider.resolveModel(model);
        ChatSession session = chatMemoryService.startTurn(sessionId, model, resolvedModel, message);
        String resolvedProvider = chatModelProviderRegistry.resolveProviderName(provider);
        ChatToolRouterService.ToolDecision routedDecision = toolDecisionService.route(message, resolvedProvider, resolvedModel);
        ChatToolRouterService.ToolDecision decision = resolveDecision(session, message, resolvedProvider, resolvedModel, routedDecision);
        log.info(
                "requestId={} chat_prepare sessionId={} provider={} model={} decisionType={} needsClarification={} usesTool={}",
                requestId,
                session.sessionId(),
                resolvedProvider,
                resolvedModel,
                decision.type(),
                decision.needsClarification(),
                decision.shouldUseTool()
        );
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
            return PreparedChat.forPrompt(chatModelProvider, buildConversationPrompt(clearedSession, message, null), resolvedModel, null, null, clearedSession);
        }

        try {
            toolPhaseListener.onPhase("tool-execution-started", toolNameForDecision(decision));
            log.info(
                    "requestId={} tool_execution_start sessionId={} provider={} model={} tool={} reason={}",
                    requestId,
                    session.sessionId(),
                    resolvedProvider,
                    resolvedModel,
                    toolNameForDecision(decision),
                    decision.reason()
            );
            ToolExecution execution = executeTool(decision);
            toolPhaseListener.onPhase("tool-execution-completed", execution.toolName());
            log.info(
                    "requestId={} tool_execution_complete sessionId={} provider={} model={} tool={} summary={}",
                    requestId,
                    session.sessionId(),
                    resolvedProvider,
                    resolvedModel,
                    execution.toolName(),
                    execution.summary()
            );
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
            return PreparedChat.forPrompt(chatModelProvider, augmentedPrompt, resolvedModel, metadata, execution.toolResult(reportsDirectory), clearedSession);
        } catch (IllegalArgumentException | McpClientException ex) {
            log.warn(
                    "requestId={} tool_execution_failed sessionId={} provider={} model={} tool={} message={}",
                    requestId,
                    session.sessionId(),
                    resolvedProvider,
                    resolvedModel,
                    toolNameForDecision(decision),
                    ex.getMessage()
            );
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

    /**
     * Completes a prepared chat with the assistant's response.
     *
     * @param preparedChat the prepared chat object
     * @param assistantResponse the assistant's response text
     * @param providerMetadata metadata from the model provider
     * @return the updated and saved chat session
     */
    public ChatSession completePreparedChat(
            PreparedChat preparedChat,
            String assistantResponse,
            net.jrodolfo.llm.dto.ModelProviderMetadata providerMetadata
    ) {
        return completePreparedChat(preparedChat, assistantResponse, providerMetadata, null);
    }

    /**
     * Completes a prepared chat with the assistant's response and a request ID.
     *
     * @param preparedChat the prepared chat object
     * @param assistantResponse the assistant's response text
     * @param providerMetadata metadata from the model provider
     * @param requestId the request ID for logging
     * @return the updated and saved chat session
     */
    public ChatSession completePreparedChat(
            PreparedChat preparedChat,
            String assistantResponse,
            net.jrodolfo.llm.dto.ModelProviderMetadata providerMetadata,
            String requestId
    ) {
        if (preparedChat.immediateResponse() != null || preparedChat.session() == null) {
            throw new IllegalArgumentException("Only prompt-based prepared chats can be completed.");
        }
        log.info(
                "requestId={} chat_persist_response sessionId={} provider={} model={}",
                requestId,
                preparedChat.session().sessionId(),
                providerMetadata != null ? providerMetadata.provider() : null,
                preparedChat.model()
        );
        return chatMemoryService.finishTurn(
                preparedChat.session(),
                assistantResponse,
                preparedChat.toolMetadata(),
                preparedChat.toolResult(),
                providerMetadata,
                preparedChat.session().pendingToolCall()
        );
    }

    /**
     * Resolves the tool decision based on the current session state and the new message.
     *
     * @param session the current chat session
     * @param message the user message
     * @param provider the model provider name
     * @param model the model name
     * @param routedDecision the initial routing decision
     * @return the resolved tool decision
     */
    private ChatToolRouterService.ToolDecision resolveDecision(
            ChatSession session,
            String message,
            String provider,
            String model,
            ChatToolRouterService.ToolDecision routedDecision
    ) {
        // Follow-up turns should try to complete an existing pending tool request before treating
        // the new message as a completely fresh routing decision.
        if (session.pendingToolCall() == null) {
            return routedDecision;
        }

        ChatToolRouterService.ToolDecision pendingDecision = toolDecisionService.resolvePending(session.pendingToolCall(), message, provider, model);
        if (pendingDecision.shouldUseTool()) {
            return pendingDecision;
        }
        if (pendingDecision.needsClarification() && routedDecision.type() == ChatToolRouterService.DecisionType.NONE) {
            return pendingDecision;
        }
        return routedDecision;
    }

    /**
     * Creates a pending tool call from a tool decision if clarification is needed.
     *
     * @param decision the tool decision
     * @return the pending tool call, or null if not needed
     */
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

    /**
     * Converts a internal pending tool call to a DTO response.
     *
     * @param pendingToolCall the pending tool call
     * @return the pending tool call response DTO
     */
    private PendingToolCallResponse toPendingToolResponse(PendingToolCall pendingToolCall) {
        return chatSessionService.toPendingToolResponse(pendingToolCall);
    }

    /**
     * Executes the tool specified in the decision.
     *
     * @param decision the tool decision
     * @return the result of the tool execution
     */
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

    /**
     * Builds the conversation prompt for the model provider.
     *
     * @param session the chat session
     * @param currentUserMessage the current user message
     * @param toolContext context from a tool execution, if any
     * @return the provider prompt
     */
    private ProviderPrompt buildConversationPrompt(ChatSession session, String currentUserMessage, ChatPromptBuilder.ToolContext toolContext) {
        List<net.jrodolfo.llm.model.ChatSessionMessage> history = chatMemoryService.historyBeforeLatestUserMessage(session);
        if (toolContext == null) {
            return chatPromptBuilder.buildPlainChatProviderPrompt(currentUserMessage, history);
        }
        return ProviderPrompt.forPrompt(chatPromptBuilder.buildToolAssistedPrompt(currentUserMessage, history, toolContext));
    }

    /**
     * Builds a failure message to be returned to the user when a tool fails.
     *
     * @param decision the tool decision
     * @param errorMessage the error message
     * @return the failure message
     */
    private String buildFailureMessage(ChatToolRouterService.ToolDecision decision, String errorMessage) {
        return "I tried to use the local tool `" + toolNameForDecision(decision) + "`, but it failed: " + errorMessage;
    }

    /**
     * Maps a decision type to a tool name string.
     *
     * @param decision the tool decision
     * @return the tool name
     */
    private String toolNameForDecision(ChatToolRouterService.ToolDecision decision) {
        return switch (decision.type()) {
            case LIST_REPORTS -> "list_recent_reports";
            case READ_LATEST_REPORT -> "read_report_summary";
            case AWS_REGION_AUDIT -> "aws_region_audit";
            case S3_CLOUDWATCH_REPORT -> "s3_cloudwatch_report";
            case NONE -> "none";
        };
    }

    /**
     * Extracts the latest run directory from a list of reports.
     *
     * @param result the result map containing reports
     * @return the latest run directory
     */
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

    /**
     * Summarizes the result of a list reports tool invocation.
     *
     * @param result the result map
     * @return a summary string
     */
    @SuppressWarnings("unchecked")
    private String summarizeListReports(Map<String, Object> result) {
        List<Map<String, Object>> reports = (List<Map<String, Object>>) result.get("reports");
        int count = reports != null ? reports.size() : 0;
        return "Found " + count + " recent report" + (count == 1 ? "" : "s") + ".";
    }

    /**
     * Summarizes the result of a read report tool invocation.
     *
     * @param result the result map
     * @return a summary string
     */
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

    /**
     * Summarizes the result of an AWS region audit tool invocation.
     *
     * @param result the result map
     * @return a summary string
     */
    @SuppressWarnings("unchecked")
    private String summarizeAudit(Map<String, Object> result) {
        Map<String, Object> summary = nestedMap(result, "summary");
        return "AWS audit completed with success_count=%s, failure_count=%s, skipped_count=%s.".formatted(
                valueOrUnknown(summary, "success_count"),
                valueOrUnknown(summary, "failure_count"),
                valueOrUnknown(summary, "skipped_count")
        );
    }

    /**
     * Summarizes the result of an S3 report tool invocation.
     *
     * @param result the result map
     * @return a summary string
     */
    @SuppressWarnings("unchecked")
    private String summarizeS3Report(Map<String, Object> result) {
        Map<String, Object> summary = nestedMap(result, "summary");
        return "S3 CloudWatch report for bucket %s completed with success_count=%s and failure_count=%s.".formatted(
                valueOrUnknown(summary, "bucket"),
                valueOrUnknown(summary, "success_count"),
                valueOrUnknown(summary, "failure_count")
        );
    }

    /**
     * Safely retrieves a nested map from a source map.
     *
     * @param source the source map
     * @param key the key for the nested map
     * @return the nested map, or an empty map if not found
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /**
     * Safely retrieves a value from a map or returns "unknown".
     *
     * @param map the map
     * @param key the key
     * @return the value or "unknown"
     */
    private Object valueOrUnknown(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "unknown" : value;
    }

    /**
     * Internal record to store tool execution results.
     *
     * @param toolName the name of the tool
     * @param result the result map
     * @param summary a summary of the result
     */
    private record ToolExecution(
            String toolName,
            Map<String, Object> result,
            String summary
    ) {
        /**
         * Converts the tool result to a structured map for the frontend.
         *
         * @param reportsDirectory the reports directory
         * @return the structured tool result
         */
        Map<String, Object> toolResult(Path reportsDirectory) {
            return structuredToolResult(toolName, result, reportsDirectory);
        }
    }

    /**
     * Structures a tool result for the frontend based on the tool name.
     *
     * @param toolName the name of the tool
     * @param result the raw result map
     * @param reportsDirectory the reports directory
     * @return the structured result map
     */
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

    /**
     * Generates a relative path for an artifact within the reports directory.
     *
     * @param runDir the run directory
     * @param fileName the file name
     * @param reportsDirectory the reports directory
     * @return the relative path string
     */
    private static String artifactPath(Object runDir, String fileName, Path reportsDirectory) {
        if (!(runDir instanceof String runDirValue) || runDirValue.isBlank()) {
            return "";
        }
        return artifactRelativePath(Path.of(runDirValue, fileName).toString(), reportsDirectory);
    }

    /**
     * Relativizes run directories in a list of reports.
     *
     * @param reportsValue the reports value
     * @param reportsDirectory the reports directory
     * @return the relativized reports
     */
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

    /**
     * Relativizes paths in failed steps.
     *
     * @param failedStepsValue the failed steps value
     * @param reportsDirectory the reports directory
     * @return the relativized failed steps
     */
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

    /**
     * Relativizes a path string against the reports directory.
     *
     * @param pathValue the path value
     * @param reportsDirectory the reports directory
     * @return the relativized path string
     */
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

    /**
     * Lightweight hand-off object between orchestration and provider execution.
     *
     * <p>An instance contains either an {@code immediateResponse} or the prompt/session state
     * needed to execute and persist a model-backed reply.
     */
    /**
     * Lightweight hand-off object between orchestration and provider execution.
     *
     * <p>An instance contains either an {@code immediateResponse} or the prompt/session state
     * needed to execute and persist a model-backed reply.
     *
     * @param provider the model provider
     * @param prompt the provider prompt
     * @param model the model name
     * @param toolMetadata metadata about the tool used
     * @param toolResult result of the tool execution
     * @param pendingTool pending tool call response
     * @param session the chat session
     * @param immediateResponse immediate response, if any
     */
    public record PreparedChat(
            ChatModelProvider provider,
            ProviderPrompt prompt,
            String model,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult,
            PendingToolCallResponse pendingTool,
            ChatSession session,
            ChatResponse immediateResponse
    ) {
        /**
         * Creates a PreparedChat for a prompt-based continuation.
         *
         * @param provider the model provider
         * @param prompt the provider prompt
         * @param model the model name
         * @param toolMetadata metadata about the tool used
         * @param toolResult result of the tool execution
         * @param session the chat session
         * @return a PreparedChat instance
         */
        static PreparedChat forPrompt(
                ChatModelProvider provider,
                ProviderPrompt prompt,
                String model,
                ChatToolMetadata toolMetadata,
                Map<String, Object> toolResult,
                ChatSession session
        ) {
            return new PreparedChat(provider, prompt, model, toolMetadata, toolResult, null, session, null);
        }

        /**
         * Creates a PreparedChat for an immediate response.
         *
         * @param response the immediate chat response
         * @return a PreparedChat instance
         */
        static PreparedChat forImmediateResponse(ChatResponse response) {
            return new PreparedChat(null, null, response.model(), response.tool(), response.toolResult(), response.pendingTool(), null, response);
        }
    }

    /**
     * Interface for listening to tool execution phases.
     */
    @FunctionalInterface
    public interface ToolPhaseListener {
        /**
         * Called when a tool phase changes.
         *
         * @param phaseType the type of the phase
         * @param toolName the name of the tool
         */
        void onPhase(String phaseType, String toolName);
    }
}
