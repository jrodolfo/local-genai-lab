package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.ReadReportSummaryToolRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ChatOrchestratorService {

    private final OllamaService ollamaService;
    private final McpService mcpService;
    private final ChatToolRouterService toolRouterService;

    public ChatOrchestratorService(
            OllamaService ollamaService,
            McpService mcpService,
            ChatToolRouterService toolRouterService
    ) {
        this.ollamaService = ollamaService;
        this.mcpService = mcpService;
        this.toolRouterService = toolRouterService;
    }

    public ChatResponse chat(String message, String model) {
        PreparedChat preparedChat = prepareChat(message, model);
        if (preparedChat.immediateResponse() != null) {
            return preparedChat.immediateResponse();
        }
        return ollamaService.chat(preparedChat.prompt(), preparedChat.model(), preparedChat.toolMetadata());
    }

    public PreparedChat prepareChat(String message, String model) {
        ChatToolRouterService.ToolDecision decision = toolRouterService.route(message);
        if (!decision.shouldUseTool()) {
            return PreparedChat.forPrompt(message, ollamaService.resolveModel(model), null);
        }

        try {
            ToolExecution execution = executeTool(decision);
            String augmentedPrompt = buildAugmentedPrompt(message, execution);
            ChatToolMetadata metadata = new ChatToolMetadata(true, execution.toolName(), "success", execution.summary());
            return PreparedChat.forPrompt(augmentedPrompt, ollamaService.resolveModel(model), metadata);
        } catch (IllegalArgumentException | McpClientException ex) {
            ChatToolMetadata metadata = new ChatToolMetadata(
                    true,
                    toolNameForDecision(decision),
                    "failed",
                    ex.getMessage()
            );
            ChatResponse fallbackResponse = new ChatResponse(
                    buildFailureMessage(decision, ex.getMessage()),
                    ollamaService.resolveModel(model),
                    metadata
            );
            return PreparedChat.forImmediateResponse(fallbackResponse);
        }
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

    private String buildAugmentedPrompt(String userMessage, ToolExecution execution) {
        return """
                The user asked:
                %s

                A local tool was used before answering.

                Tool name:
                %s

                Tool summary:
                %s

                Tool result payload:
                %s

                Answer the user using the tool result. Be concise, factual, and grounded in the provided tool output.
                If the tool result is incomplete, say so explicitly.
                """.formatted(
                userMessage.trim(),
                execution.toolName(),
                execution.summary(),
                execution.result().toString()
        );
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
            ChatResponse immediateResponse
    ) {
        static PreparedChat forPrompt(String prompt, String model, ChatToolMetadata toolMetadata) {
            return new PreparedChat(prompt, model, toolMetadata, null);
        }

        static PreparedChat forImmediateResponse(ChatResponse response) {
            return new PreparedChat(null, response.model(), response.tool(), response);
        }
    }
}
