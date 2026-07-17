package net.jrodolfo.llm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coordinates the backend lifecycle for one Agent chat turn.
 *
 * <p>The orchestrator owns the decision boundary between plain chat, pending
 * tool clarification, MCP-backed tool execution, and provider invocation. It
 * also ensures that every completed turn is persisted through
 * {@link ChatMemoryService}, so controller code does not need to know whether a
 * response came directly from a model or from a tool-assisted prompt.
 */
@Service
public class ChatOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestratorService.class);
    private static final ObjectMapper TOOL_RESULT_OBJECT_MAPPER = new ObjectMapper();

    private final ChatModelProviderRegistry chatModelProviderRegistry;
    private final McpService mcpService;
    private final ToolDecisionService toolDecisionService;
    private final ChatMemoryService chatMemoryService;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatSessionService chatSessionService;
    private final Path reportsDirectory;
    private static final ToolPhaseListener NOOP_TOOL_PHASE_LISTENER = (phaseType, toolName) -> {
    };

    /**
     * Constructs a new ChatOrchestratorService.
     *
     * @param chatModelProviderRegistry the registry of chat model providers
     * @param mcpService                the service for MCP tools
     * @param toolDecisionService       the service for making tool decisions
     * @param chatMemoryService         the service for managing chat memory
     * @param chatPromptBuilder         the builder for chat prompts
     * @param chatSessionService        the service for chat sessions
     * @param appStorageProperties      application storage properties
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
     * Executes and persists a non-streaming chat turn.
     *
     * @param message   user message for this turn
     * @param provider  requested provider, or null/blank for the configured default
     * @param model     requested model, or null/blank for the provider default
     * @param sessionId existing session id, or null/blank to create a new session
     * @return complete chat response with session id, tool metadata, and provider metadata when available
     */
    public ChatResponse chat(String message, String provider, String model, String sessionId) {
        return chat(message, provider, model, sessionId, null);
    }

    /**
     * Executes and persists a non-streaming chat turn with request correlation.
     *
     * @param message   user message for this turn
     * @param provider  requested provider, or null/blank for the configured default
     * @param model     requested model, or null/blank for the provider default
     * @param sessionId existing session id, or null/blank to create a new session
     * @param requestId request id used only for structured logs
     * @return complete chat response with session id, tool metadata, and provider metadata when available
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
        String assistantResponse = clarifyCompletedToolResponse(
                response.response(),
                preparedChat.toolMetadata(),
                preparedChat.toolResult()
        );
        PendingToolCall pendingToolCall = inferPendingToolCallFromAssistantResponse(assistantResponse, preparedChat);
        chatMemoryService.finishTurn(
                preparedChat.session(),
                assistantResponse,
                response.tool(),
                response.toolResult(),
                response.metadata(),
                pendingToolCall
        );
        return new ChatResponse(
                assistantResponse,
                response.model(),
                response.tool(),
                response.toolResult(),
                response.sessionId(),
                toPendingToolResponse(pendingToolCall),
                response.metadata()
        );
    }

    /**
     * Prepares a turn before the caller chooses normal or streaming provider execution.
     *
     * <p>The result is either an immediate response for clarification/failure
     * paths, or a prompt-backed continuation that the controller can pass to a
     * provider. This split is what allows streaming responses to emit explicit
     * backend tool phases before provider tokens begin.
     *
     * @param message   user message for this turn
     * @param provider  requested provider, or null/blank for the configured default
     * @param model     requested model, or null/blank for the provider default
     * @param sessionId existing session id, or null/blank to create a new session
     * @return prepared immediate response or provider continuation
     */
    public PreparedChat prepareChat(String message, String provider, String model, String sessionId) {
        return prepareChat(message, provider, model, sessionId, null);
    }

    /**
     * Prepares a chat turn and includes the current request id in orchestration logs.
     *
     * @param message   the user message
     * @param provider  the model provider name
     * @param model     the model name
     * @param sessionId the session ID
     * @param requestId the request ID for logging
     * @return the prepared chat object
     */
    public PreparedChat prepareChat(String message, String provider, String model, String sessionId, String requestId) {
        return prepareChat(message, provider, model, sessionId, requestId, NOOP_TOOL_PHASE_LISTENER);
    }

    /**
     * Prepares a chat turn with full context and tool-phase callbacks.
     *
     * @param message           user message for this turn
     * @param provider          requested provider, or null/blank for the configured default
     * @param model             requested model, or null/blank for the provider default
     * @param sessionId         existing session id, or null/blank to create a new session
     * @param requestId         request id used only for structured logs
     * @param toolPhaseListener callback used by streaming transports to surface tool progress
     * @return prepared immediate response or provider continuation
     */
    public PreparedChat prepareChat(
            String message,
            String provider,
            String model,
            String sessionId,
            String requestId,
            ToolPhaseListener toolPhaseListener
    ) {
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
        if (decision.needsClarification() || decision.shouldUseTool()) {
            toolPhaseListener.onPhase("tool-decision-started", toolNameForDecision(decision));
        }
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
            ChatToolMetadata metadata = new ChatToolMetadata(
                    true,
                    execution.toolName(),
                    toolStatusForExecution(execution),
                    execution.summary()
            );
            Map<String, Object> structuredToolResult = execution.toolResult(reportsDirectory);
            if (shouldReturnImmediateToolResponse(metadata, structuredToolResult)) {
                String assistantResponse = buildS3CompletionMessage(structuredToolResult);
                ChatSession persistedSession = chatMemoryService.finishTurn(
                        clearedSession,
                        assistantResponse,
                        metadata,
                        structuredToolResult,
                        null,
                        null
                );
                return PreparedChat.forImmediateResponse(new ChatResponse(
                        assistantResponse,
                        resolvedModel,
                        metadata,
                        structuredToolResult,
                        persistedSession.sessionId(),
                        null,
                        null
                ));
            }
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
            return PreparedChat.forPrompt(chatModelProvider, augmentedPrompt, resolvedModel, metadata, structuredToolResult, clearedSession);
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
     * Persists the assistant response for a prompt-backed prepared chat.
     *
     * @param preparedChat      prepared prompt continuation returned by {@link #prepareChat(String, String, String, String)}
     * @param assistantResponse final assistant text produced by the provider
     * @param providerMetadata  provider/model timing and token metadata, when supplied
     * @return updated and saved session
     */
    public ChatSession completePreparedChat(
            PreparedChat preparedChat,
            String assistantResponse,
            net.jrodolfo.llm.dto.ModelProviderMetadata providerMetadata
    ) {
        return completePreparedChat(preparedChat, assistantResponse, providerMetadata, null);
    }

    /**
     * Persists the assistant response for a prompt-backed prepared chat with request correlation.
     *
     * @param preparedChat      prepared prompt continuation returned by {@code prepareChat}
     * @param assistantResponse final assistant text produced by the provider
     * @param providerMetadata  provider/model timing and token metadata, when supplied
     * @param requestId         request id used only for structured logs
     * @return updated and saved session
     * @throws IllegalArgumentException when called for an immediate-response preparation
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
        String finalAssistantResponse = clarifyCompletedToolResponse(
                assistantResponse,
                preparedChat.toolMetadata(),
                preparedChat.toolResult()
        );
        PendingToolCall pendingToolCall = inferPendingToolCallFromAssistantResponse(finalAssistantResponse, preparedChat);
        return chatMemoryService.finishTurn(
                preparedChat.session(),
                finalAssistantResponse,
                preparedChat.toolMetadata(),
                preparedChat.toolResult(),
                providerMetadata,
                pendingToolCall
        );
    }

    /**
     * Returns an immediate response for prepared chats that should not continue into provider
     * generation.
     *
     * <p>This protects streaming transports from emitting model-generated follow-up prose after a
     * successful local tool already produced the final user-facing result.
     *
     * @param preparedChat prepared chat returned by {@code prepareChat}
     * @return existing or synthesized immediate response, or {@code null} when provider generation should continue
     */
    public ChatResponse materializeImmediateResponse(PreparedChat preparedChat) {
        if (preparedChat == null) {
            return null;
        }
        if (preparedChat.immediateResponse() != null) {
            return preparedChat.immediateResponse();
        }
        if (!shouldReturnImmediateToolResponse(preparedChat.toolMetadata(), preparedChat.toolResult())
                || preparedChat.session() == null) {
            return null;
        }

        String assistantResponse = buildS3CompletionMessage(preparedChat.toolResult());
        ChatSession persistedSession = chatMemoryService.finishTurn(
                preparedChat.session(),
                assistantResponse,
                preparedChat.toolMetadata(),
                preparedChat.toolResult(),
                null,
                null
        );
        return new ChatResponse(
                assistantResponse,
                preparedChat.model(),
                preparedChat.toolMetadata(),
                preparedChat.toolResult(),
                persistedSession.sessionId(),
                null,
                null
        );
    }

    /**
     * Resolves whether the new user message completes a pending tool request.
     *
     * <p>Pending tool calls take precedence over fresh routing only when the new
     * message supplies the missing information or still needs clarification.
     * Otherwise the fresh routing decision wins, so users can change direction.
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
                    decision.bucketOptions(),
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
                    decision.bucketOptions(),
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
     * Creates structured pending state when a tool-assisted answer recommends a
     * supported next tool action and asks the user whether to proceed.
     *
     * @param assistantResponse final provider response text
     * @param preparedChat      prepared chat state containing tool metadata/result
     * @return pending tool call state, or null when no supported next action was recommended
     */
    private PendingToolCall inferPendingToolCallFromAssistantResponse(String assistantResponse, PreparedChat preparedChat) {
        if (assistantResponse == null || preparedChat == null || preparedChat.toolMetadata() == null || preparedChat.toolResult() == null) {
            return null;
        }
        if (!"aws_region_audit".equals(preparedChat.toolMetadata().name()) || !isCompletedAwsAuditStatus(preparedChat.toolMetadata().status())) {
            return null;
        }

        String normalized = assistantResponse.toLowerCase(Locale.ROOT);
        boolean recommendsS3Report = normalized.contains("s3")
                && normalized.contains("report")
                && (normalized.contains("recommend") || normalized.contains("next step") || normalized.contains("proceed"));
        if (!recommendsS3Report) {
            return null;
        }

        List<String> bucketOptions = extractBucketOptions(preparedChat.toolResult());
        return new PendingToolCall(
                ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT,
                null,
                null,
                null,
                inferRecommendedDays(normalized),
                "assistant recommended s3 cloudwatch report",
                List.of(),
                bucketOptions,
                bucketOptions.size() == 1 ? List.of("confirmation") : List.of("bucket")
        );
    }

    /**
     * Extracts bucket options from a structured audit tool result.
     *
     * @param toolResult structured tool result
     * @return bucket names in artifact order
     */
    private List<String> extractBucketOptions(Map<String, Object> toolResult) {
        Object value = toolResult.get("bucketNames");
        if (!(value instanceof List<?> buckets)) {
            return List.of();
        }
        return buckets.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(bucket -> !bucket.isBlank())
                .toList();
    }

    /**
     * Infers the recommended S3 report lookback window from model wording.
     *
     * @param normalizedResponse lowercase assistant response
     * @return the lookback window in days
     */
    private Integer inferRecommendedDays(String normalizedResponse) {
        if (normalizedResponse.contains("last month") || normalizedResponse.contains("past month") || normalizedResponse.contains("previous month")) {
            return 30;
        }
        return null;
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
                Map<String, Object> enrichedResult = enrichAuditResult(response.result(), services);
                yield new ToolExecution(response.tool(), enrichedResult, summarizeAudit(enrichedResult));
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
     * @param session            the chat session
     * @param currentUserMessage the current user message
     * @param toolContext        context from a tool execution, if any
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
     * @param decision     the tool decision
     * @param errorMessage the error message
     * @return the failure message
     */
    private String buildFailureMessage(ChatToolRouterService.ToolDecision decision, String errorMessage) {
        return "I tried to use the local tool `" + toolNameForDecision(decision) + "`, but it failed: " + errorMessage;
    }

    /**
     * Replaces contradictory model prose when a tool already completed successfully.
     *
     * <p>Streaming responses depend primarily on prompt rules because tokens are already emitted.
     * This guardrail keeps non-streaming responses and persisted session text from saying that a
     * completed S3 report will be run in the future.
     *
     * @param assistantResponse provider-generated assistant text
     * @param toolMetadata      metadata for the tool used in this turn
     * @param toolResult        structured tool result
     * @return original text, or a deterministic completion message for contradictory S3 text
     */
    private String clarifyCompletedToolResponse(
            String assistantResponse,
            ChatToolMetadata toolMetadata,
            Map<String, Object> toolResult
    ) {
        if (toolMetadata == null
                || toolResult == null
                || !"s3_cloudwatch_report".equals(toolMetadata.name())
                || !"success".equals(toolMetadata.status())) {
            return assistantResponse;
        }

        return buildS3CompletionMessage(toolResult);
    }

    /**
     * Detects future-tense or repeat-recommendation wording after a completed S3 report.
     *
     * @param assistantResponse provider-generated assistant text
     * @return true when the response contradicts the completed tool state
     */
    private boolean hasContradictoryS3CompletionWording(String assistantResponse) {
        if (assistantResponse == null || assistantResponse.isBlank()) {
            return true;
        }

        String normalized = assistantResponse.toLowerCase(Locale.ROOT);
        return normalized.contains("will proceed")
                || normalized.contains("will run")
                || normalized.contains("will now run")
                || normalized.contains("should run")
                || normalized.contains("already been completed")
                || normalized.contains("has already been completed")
                || normalized.contains("you can view the report in /app/")
                || normalized.contains("you can view the report in the /app/")
                || normalized.contains("should provide more granular")
                || normalized.contains("suggest running an s3 report")
                || normalized.contains("recommend running an s3 report")
                || normalized.contains("suggest that you run an s3 report")
                || normalized.contains("next step by running an s3 report");
    }

    /**
     * Builds a deterministic S3 report completion message from structured tool output.
     *
     * @param toolResult structured S3 report result
     * @return user-facing completion message
     */
    private String buildS3CompletionMessage(Map<String, Object> toolResult) {
        String bucket = String.valueOf(toolResult.getOrDefault("bucket", "unknown"));
        String successCount = String.valueOf(toolResult.getOrDefault("successCount", "unknown"));
        String failureCount = String.valueOf(toolResult.getOrDefault("failureCount", "unknown"));
        String skippedCount = String.valueOf(toolResult.getOrDefault("skippedCount", "unknown"));
        String runDir = String.valueOf(toolResult.getOrDefault("runDir", ""));
        String summaryPath = String.valueOf(toolResult.getOrDefault("summaryPath", ""));
        String reportPath = String.valueOf(toolResult.getOrDefault("reportPath", ""));

        StringBuilder message = new StringBuilder();
        message.append("S3 CloudWatch report completed for bucket `").append(bucket).append("`.\n\n");
        message.append("Results: success_count=").append(successCount)
                .append(", failure_count=").append(failureCount)
                .append(", skipped_count=").append(skippedCount)
                .append(".");

        // Keep the deterministic fallback concise; the structured tool result card
        // already exposes artifact paths and file actions when the user needs them.
        if (!runDir.isBlank() || !summaryPath.isBlank() || !reportPath.isBlank()) {
            message.append("\n\nArtifacts are available in the tool result card.");
        }

        return message.toString();
    }

    private boolean shouldReturnImmediateToolResponse(ChatToolMetadata metadata, Map<String, Object> toolResult) {
        return metadata != null
                && toolResult != null
                && "s3_cloudwatch_report".equals(metadata.name())
                && "success".equals(metadata.status());
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
        List<String> bucketNames = result.get("bucketNames") instanceof List<?> buckets
                ? buckets.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        Map<String, Object> summary = nestedMap(result, "summary");
        List<String> selectedServices = stringList(summary.getOrDefault("selected_services", result.get("selected_services")));
        if (!bucketNames.isEmpty() && selectedServices.size() == 1 && selectedServices.contains("s3")) {
            return "S3 bucket discovery completed with bucket_count=%s.".formatted(bucketNames.size());
        }

        String template = hasAuditFailures(summary)
                ? "AWS audit completed with failures: success_count=%s, failure_count=%s, skipped_count=%s."
                : "AWS audit completed with success_count=%s, failure_count=%s, skipped_count=%s.";
        return template.formatted(
                valueOrUnknown(summary, "success_count"),
                valueOrUnknown(summary, "failure_count"),
                valueOrUnknown(summary, "skipped_count")
        );
    }

    /**
     * Adds first-class bucket names to S3-scoped audit results when the audit artifact exists.
     *
     * @param result   raw MCP result
     * @param services selected services for the audit
     * @return enriched result for the model prompt and UI
     */
    private Map<String, Object> enrichAuditResult(Map<String, Object> result, List<String> services) {
        Map<String, Object> enriched = new LinkedHashMap<>(result);
        if (services != null && !services.isEmpty()) {
            enriched.put("selected_services", services);
        }
        List<String> bucketNames = readS3BucketNames(result.get("run_dir"));
        if (!bucketNames.isEmpty()) {
            enriched.put("bucketNames", bucketNames);
        }
        return enriched;
    }

    /**
     * Reads S3 bucket names from the standard audit artifact.
     *
     * @param runDirValue raw run directory value from the MCP result
     * @return bucket names in artifact order
     */
    private List<String> readS3BucketNames(Object runDirValue) {
        if (!(runDirValue instanceof String runDir) || runDir.isBlank()) {
            return List.of();
        }

        Path artifactPath = resolveReportPath(runDir, "json/s3_list_buckets.json");
        if (artifactPath == null || !Files.isRegularFile(artifactPath)) {
            return List.of();
        }
        try {
            if (Files.size(artifactPath) == 0) {
                return List.of();
            }
        } catch (IOException ex) {
            log.warn("Could not inspect S3 bucket audit artifact path={}", artifactPath, ex);
            return List.of();
        }

        try {
            List<Map<String, Object>> buckets = TOOL_RESULT_OBJECT_MAPPER.readValue(
                    artifactPath.toFile(),
                    new TypeReference<>() {
                    }
            );
            List<String> names = new ArrayList<>();
            for (Map<String, Object> bucket : buckets) {
                Object name = bucket.get("Name");
                if (name instanceof String bucketName && !bucketName.isBlank()) {
                    names.add(bucketName);
                }
            }
            return names;
        } catch (IOException ex) {
            log.warn("Could not read S3 bucket audit artifact path={}", artifactPath, ex);
            return List.of();
        }
    }

    /**
     * Resolves a generated report artifact path without allowing reads outside the reports root.
     *
     * @param runDir       raw run directory
     * @param relativeFile relative file inside the run directory
     * @return normalized artifact path, or null when outside the reports root
     */
    private Path resolveReportPath(String runDir, String relativeFile) {
        List<Path> candidates = new ArrayList<>();
        Path rawRunDir = Path.of(runDir);
        if (rawRunDir.isAbsolute()) {
            candidates.add(rawRunDir.resolve(relativeFile));
        } else {
            candidates.add(reportsDirectory.resolve(rawRunDir).resolve(relativeFile));
            if (runDir.startsWith("reports/")) {
                candidates.add(reportsDirectory.resolve(runDir.substring("reports/".length())).resolve(relativeFile));
            }
        }

        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (normalized.startsWith(reportsDirectory) && Files.exists(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    /**
     * Converts a list-like value into strings.
     *
     * @param value raw value
     * @return string list
     */
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
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
     * @param key    the key for the nested map
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
     * @param result   the result map
     * @param summary  a summary of the result
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
     * @param toolName         the name of the tool
     * @param result           the raw result map
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
                Map<String, Object> structured = new LinkedHashMap<>();
                structured.put("type", "audit_summary");
                structured.put("reportType", String.valueOf(result.getOrDefault("report_type", "audit")));
                structured.put("runDir", artifactRelativePath(result.getOrDefault("run_dir", ""), reportsDirectory));
                structured.put("summaryPath", artifactPath(result.get("run_dir"), "summary.json", reportsDirectory));
                structured.put("reportPath", artifactPath(result.get("run_dir"), "report.txt", reportsDirectory));
                structured.put("accountId", String.valueOf(summary.getOrDefault("account_id", result.getOrDefault("account_id", ""))));
                structured.put("selectedRegions", summary.getOrDefault("selected_regions", result.getOrDefault("selected_regions", List.of())));
                structured.put("selectedServices", summary.getOrDefault("selected_services", result.getOrDefault("selected_services", List.of())));
                structured.put("bucketNames", result.getOrDefault("bucketNames", List.of()));
                structured.put("status", hasAuditFailures(summary) ? "partial-success" : "success");
                putIfPresent(structured, "successCount", summary.get("success_count"));
                putIfPresent(structured, "failureCount", summary.get("failure_count"));
                putIfPresent(structured, "skippedCount", summary.get("skipped_count"));
                structured.put("failedSteps", relativizeFailedSteps(summary.getOrDefault("failed_commands", result.getOrDefault("failed_steps", List.of())), reportsDirectory));
                yield structured;
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
     * Adds a structured field only when the source value is present.
     *
     * @param target target map
     * @param key    target key
     * @param value  value to add
     */
    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /**
     * Generates a relative path for an artifact within the reports directory.
     *
     * @param runDir           the run directory
     * @param fileName         the file name
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
     * @param reportsValue     the reports value
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
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    Object title = stepMap.get("title");
                    Object stepName = stepMap.getOrDefault("step", title);
                    if (stepName != null) {
                        normalized.put("step", stepName);
                    }
                    if (title != null) {
                        normalized.put("title", title);
                    }
                    stepMap.forEach((key, value) -> {
                        if ("step".equals(key) || "title".equals(key)) {
                            return;
                        }
                        normalized.put(
                                key,
                                "stderr_path".equals(key)
                                        ? artifactRelativePath(value, reportsDirectory)
                                        : value
                        );
                    });
                    return normalized;
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private String toolStatusForExecution(ToolExecution execution) {
        if (!"aws_region_audit".equals(execution.toolName())) {
            return "success";
        }
        Map<String, Object> summary = execution.result().get("summary") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return hasAuditFailures(summary) ? "partial-success" : "success";
    }

    private boolean isCompletedAwsAuditStatus(String status) {
        return "success".equals(status) || "partial-success".equals(status);
    }

    private static boolean hasAuditFailures(Map<String, Object> summary) {
        return numericValue(summary.get("failure_count")) > 0;
    }

    private static long numericValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    /**
     * Relativizes a path string against the reports directory.
     *
     * @param pathValue        the path value
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
     *
     * @param provider          the model provider
     * @param prompt            the provider prompt
     * @param model             the model name
     * @param toolMetadata      metadata about the tool used
     * @param toolResult        result of the tool execution
     * @param pendingTool       pending tool call response
     * @param session           the chat session
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
         * @param provider     the model provider
         * @param prompt       the provider prompt
         * @param model        the model name
         * @param toolMetadata metadata about the tool used
         * @param toolResult   result of the tool execution
         * @param session      the chat session
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
         * @param toolName  the name of the tool
         */
        void onPhase(String phaseType, String toolName);
    }
}
