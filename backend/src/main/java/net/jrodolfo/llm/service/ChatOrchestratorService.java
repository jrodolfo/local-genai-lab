package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.ReadReportSummaryToolRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.model.ChatSessionMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatOrchestratorService {

    private final OllamaService ollamaService;
    private final McpService mcpService;
    private final ChatToolRouterService toolRouterService;
    private final ChatMemoryService chatMemoryService;

    public ChatOrchestratorService(
            OllamaService ollamaService,
            McpService mcpService,
            ChatToolRouterService toolRouterService,
            ChatMemoryService chatMemoryService
    ) {
        this.ollamaService = ollamaService;
        this.mcpService = mcpService;
        this.toolRouterService = toolRouterService;
        this.chatMemoryService = chatMemoryService;
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
                preparedChat.session().sessionId()
        );
        chatMemoryService.finishTurn(preparedChat.session(), response.response(), response.tool());
        return response;
    }

    public PreparedChat prepareChat(String message, String model, String sessionId) {
        String resolvedModel = ollamaService.resolveModel(model);
        ChatSession session = chatMemoryService.startTurn(sessionId, model, resolvedModel, message);
        ChatToolRouterService.ToolDecision decision = toolRouterService.route(message);
        if (decision.needsClarification()) {
            ChatToolMetadata metadata = new ChatToolMetadata(
                    true,
                    toolNameForDecision(decision),
                    "clarification-needed",
                    decision.clarification()
            );
            ChatSession persistedSession = chatMemoryService.finishTurn(session, decision.clarification(), metadata);
            return PreparedChat.forImmediateResponse(new ChatResponse(
                    decision.clarification(),
                    resolvedModel,
                    metadata,
                    persistedSession.sessionId()
            ));
        }

        if (!decision.shouldUseTool()) {
            return PreparedChat.forPrompt(buildConversationPrompt(session, message, null, null), resolvedModel, null, session);
        }

        try {
            ToolExecution execution = executeTool(decision);
            String augmentedPrompt = buildConversationPrompt(session, message, execution, decision.reason());
            ChatToolMetadata metadata = new ChatToolMetadata(true, execution.toolName(), "success", execution.summary());
            return PreparedChat.forPrompt(augmentedPrompt, resolvedModel, metadata, session);
        } catch (IllegalArgumentException | McpClientException ex) {
            ChatToolMetadata metadata = new ChatToolMetadata(
                    true,
                    toolNameForDecision(decision),
                    "failed",
                    ex.getMessage()
            );
            ChatSession persistedSession = chatMemoryService.finishTurn(session, buildFailureMessage(decision, ex.getMessage()), metadata);
            ChatResponse fallbackResponse = new ChatResponse(
                    buildFailureMessage(decision, ex.getMessage()),
                    resolvedModel,
                    metadata,
                    persistedSession.sessionId()
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

    private String buildConversationPrompt(
            ChatSession session,
            String currentUserMessage,
            ToolExecution toolExecution,
            String toolReason
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are a concise, factual assistant.\n");
        builder.append("Use the conversation history below when it is relevant.\n");

        List<ChatSessionMessage> history = chatMemoryService.historyBeforeLatestUserMessage(session);
        if (!history.isEmpty()) {
            builder.append("\n<conversation_history>\n");
            for (ChatSessionMessage message : history) {
                builder.append(message.role()).append(": ").append(message.content()).append("\n");
            }
            builder.append("</conversation_history>\n");
        }

        builder.append("\n<current_user_message>\n");
        builder.append(currentUserMessage.trim()).append("\n");
        builder.append("</current_user_message>\n");

        if (toolExecution != null) {
            builder.append("\n<tool_context>\n");
            builder.append("tool_reason: ").append(toolReason).append("\n");
            builder.append("tool_name: ").append(toolExecution.toolName()).append("\n");
            builder.append("tool_summary: ").append(toolExecution.summary()).append("\n");
            builder.append("tool_result: ").append(toolExecution.result()).append("\n");
            builder.append("</tool_context>\n");
        }

        builder.append("\nAnswer the current user message directly. ");
        builder.append("If tool output is present, ground your answer in it. ");
        builder.append("If the available context is incomplete, say so explicitly.");
        return builder.toString();
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
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        String reportType = String.valueOf(result.getOrDefault("report_type", "report"));
        Object successCount = summary != null ? summary.get("success_count") : null;
        Object failureCount = summary != null ? summary.get("failure_count") : null;
        return "Read " + reportType + " with success_count=%s and failure_count=%s.".formatted(successCount, failureCount);
    }

    @SuppressWarnings("unchecked")
    private String summarizeAudit(Map<String, Object> result) {
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        return "AWS audit completed with success_count=%s, failure_count=%s, skipped_count=%s.".formatted(
                summary.get("success_count"),
                summary.get("failure_count"),
                summary.get("skipped_count")
        );
    }

    @SuppressWarnings("unchecked")
    private String summarizeS3Report(Map<String, Object> result) {
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        return "S3 CloudWatch report for bucket %s completed with success_count=%s and failure_count=%s.".formatted(
                summary.get("bucket"),
                summary.get("success_count"),
                summary.get("failure_count")
        );
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
            ChatSession session,
            ChatResponse immediateResponse
    ) {
        static PreparedChat forPrompt(String prompt, String model, ChatToolMetadata toolMetadata, ChatSession session) {
            return new PreparedChat(prompt, model, toolMetadata, session, null);
        }

        static PreparedChat forImmediateResponse(ChatResponse response) {
            return new PreparedChat(null, response.model(), response.tool(), null, response);
        }
    }
}
