package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.model.PendingToolCall;
import net.jrodolfo.llm.provider.ChatModelProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmToolPlannerService {

    private static final Pattern REGION_PATTERN = Pattern.compile("\\b(af|ap|ca|eu|il|me|sa|us)-[a-z]+-\\d\\b");
    private static final Pattern BUCKET_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,253}[a-z0-9]$");
    private static final List<String> AUDIT_SERVICES = List.of(
            "sts", "aws-config", "s3", "ec2", "elbv2", "rds", "lambda",
            "ecs", "eks", "sagemaker", "opensearch", "secretsmanager", "logs", "tagging"
    );

    private final ChatModelProvider chatModelProvider;
    private final ObjectMapper objectMapper;

    public LlmToolPlannerService(ChatModelProvider chatModelProvider, ObjectMapper objectMapper) {
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
    }

    public Optional<ChatToolRouterService.ToolDecision> plan(String message, String model) {
        return planDetailed(message, model).parsedDecision();
    }

    public PlanningResult planDetailed(String message, String model) {
        String plannerPrompt = """
                <tool_planning_request>
                You are a routing planner for a local chat application.
                Decide whether the backend should use one of its existing tools.

                Respond with JSON only. Do not add markdown. Do not answer the user directly.

                Allowed actions:
                - none
                - use_tool
                - clarification_needed

                Allowed tool names:
                - list_recent_reports
                - read_report_summary
                - aws_region_audit
                - s3_cloudwatch_report

                Return this shape:
                {
                  "action": "none | use_tool | clarification_needed",
                  "toolName": "allowed tool name or null",
                  "arguments": {
                    "reportType": "audit | s3_cloudwatch | all",
                    "bucket": "bucket name",
                    "region": "aws region",
                    "days": 14,
                    "services": ["sts", "ec2"]
                  },
                  "missingFields": ["bucket"],
                  "reason": "short reason"
                }

                Use clarification_needed only when the intent is clearly a supported tool request but required arguments are missing or ambiguous.
                Use none when no supported tool should run.
                For "read the latest report" ambiguity, use toolName "read_report_summary" and missingFields ["reportType"].
                For report listings, use toolName "list_recent_reports".
                For latest report reading, use toolName "read_report_summary".
                For aws audits, services must be from this allowlist:
                %s

                <user_message>
                %s
                </user_message>
                </tool_planning_request>
                """.formatted(String.join(", ", AUDIT_SERVICES), message.trim());

        String rawResponse = chatModelProvider.chat(plannerPrompt, model, null, null, null).response();
        return new PlanningResult(rawResponse, parseDecision(rawResponse, message, true));
    }

    public Optional<ChatToolRouterService.ToolDecision> resolvePending(PendingToolCall pendingToolCall, String message, String model) {
        return resolvePendingDetailed(pendingToolCall, message, model).parsedDecision();
    }

    public PlanningResult resolvePendingDetailed(PendingToolCall pendingToolCall, String message, String model) {
        String plannerPrompt = """
                <tool_planning_request>
                You are resolving a follow-up message for a pending tool request.
                Respond with JSON only. Do not add markdown. Do not answer the user directly.

                Allowed actions:
                - none
                - use_tool
                - clarification_needed

                Allowed tool names:
                - list_recent_reports
                - read_report_summary
                - aws_region_audit
                - s3_cloudwatch_report

                Return this shape:
                {
                  "action": "none | use_tool | clarification_needed",
                  "toolName": "allowed tool name or null",
                  "arguments": {
                    "reportType": "audit | s3_cloudwatch | all",
                    "bucket": "bucket name",
                    "region": "aws region",
                    "days": 14,
                    "services": ["sts", "ec2"]
                  },
                  "missingFields": ["bucket"],
                  "reason": "short reason"
                }

                Pending tool:
                - type: %s
                - current reportType: %s
                - current bucket: %s
                - current region: %s
                - current days: %s
                - current services: %s
                - missing fields: %s
                - reason: %s

                If the follow-up still does not resolve the pending tool request, return clarification_needed with the remaining missing fields.
                If the message is clearly unrelated, return none.

                <user_message>
                %s
                </user_message>
                </tool_planning_request>
                """.formatted(
                pendingToolCall.type(),
                pendingToolCall.reportType(),
                pendingToolCall.bucket(),
                pendingToolCall.region(),
                pendingToolCall.days(),
                pendingToolCall.services(),
                pendingToolCall.missingFields(),
                pendingToolCall.reason(),
                message.trim()
        );

        String rawResponse = chatModelProvider.chat(plannerPrompt, model, null, null, null).response();
        return new PlanningResult(rawResponse, parseDecision(rawResponse, message, false));
    }

    private Optional<ChatToolRouterService.ToolDecision> parseDecision(String rawResponse, String originalMessage, boolean strictIntentChecks) {
        String json = extractJsonObject(rawResponse);
        if (json == null) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            String action = textValue(root.get("action"));
            if (action == null) {
                return Optional.empty();
            }

            if ("none".equals(action)) {
                return Optional.of(ChatToolRouterService.ToolDecision.none());
            }

            ChatToolRouterService.DecisionType type = toDecisionType(textValue(root.get("toolName")));
            if (type == null) {
                return Optional.empty();
            }

            JsonNode argumentsNode = root.get("arguments");
            String reportType = normalizeReportType(textValue(argumentsNode != null ? argumentsNode.get("reportType") : null));
            String bucket = textValue(argumentsNode != null ? argumentsNode.get("bucket") : null);
            String region = normalizeRegion(textValue(argumentsNode != null ? argumentsNode.get("region") : null));
            Integer days = normalizeDays(argumentsNode != null ? argumentsNode.get("days") : null);
            List<String> services = normalizeServices(argumentsNode != null ? argumentsNode.get("services") : null);
            List<String> missingFields = normalizeMissingFields(root.get("missingFields"));
            String reason = normalizedReason(textValue(root.get("reason")));

            if ("clarification_needed".equals(action)) {
                return Optional.of(applyGuardrails(new ChatToolRouterService.ToolDecision(
                        type,
                        reportType,
                        bucket,
                        region,
                        days,
                        reason,
                        services,
                        clarificationFor(type, missingFields)
                ), originalMessage, strictIntentChecks));
            }

            if (!"use_tool".equals(action)) {
                return Optional.empty();
            }

            return Optional.of(applyGuardrails(new ChatToolRouterService.ToolDecision(
                    type,
                    reportType,
                    bucket,
                    region,
                    days,
                    reason,
                    services
            ), originalMessage, strictIntentChecks));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String extractJsonObject(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        int start = rawResponse.indexOf('{');
        int end = rawResponse.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return rawResponse.substring(start, end + 1);
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private ChatToolRouterService.DecisionType toDecisionType(String toolName) {
        if (toolName == null) {
            return null;
        }
        return switch (toolName) {
            case "list_recent_reports" -> ChatToolRouterService.DecisionType.LIST_REPORTS;
            case "read_report_summary" -> ChatToolRouterService.DecisionType.READ_LATEST_REPORT;
            case "aws_region_audit" -> ChatToolRouterService.DecisionType.AWS_REGION_AUDIT;
            case "s3_cloudwatch_report" -> ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT;
            default -> null;
        };
    }

    private String normalizeReportType(String reportType) {
        if (reportType == null) {
            return null;
        }
        return switch (reportType.trim().toLowerCase(Locale.ROOT)) {
            case "audit" -> "audit";
            case "s3_cloudwatch", "s3-cloudwatch", "s3" -> "s3_cloudwatch";
            case "all" -> "all";
            default -> null;
        };
    }

    private String normalizeRegion(String region) {
        if (region == null) {
            return null;
        }
        Matcher matcher = REGION_PATTERN.matcher(region.trim().toLowerCase(Locale.ROOT));
        return matcher.find() ? matcher.group() : null;
    }

    private Integer normalizeDays(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        int days = node.asInt(-1);
        if (days <= 0) {
            return null;
        }
        return Math.min(days, 365);
    }

    private List<String> normalizeServices(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        Set<String> services = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String service = textValue(item);
            if (service != null && AUDIT_SERVICES.contains(service)) {
                services.add(service);
            }
        }
        return new ArrayList<>(services);
    }

    private List<String> normalizeMissingFields(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        Set<String> fields = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String field = textValue(item);
            if (field != null) {
                fields.add(field);
            }
        }
        return new ArrayList<>(fields);
    }

    private String clarificationFor(ChatToolRouterService.DecisionType type, List<String> missingFields) {
        if (type == ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT && missingFields.contains("bucket")) {
            return "I can run the S3 CloudWatch report, but I need the bucket name.";
        }
        if (type == ChatToolRouterService.DecisionType.READ_LATEST_REPORT && missingFields.contains("reportType")) {
            return "I can read the latest report, but I need to know whether you want the latest audit report or the latest s3 cloudwatch report.";
        }
        return "I need a bit more information before I can use that tool.";
    }

    private String normalizedReason(String reason) {
        return reason == null ? "llm tool planning decision" : reason;
    }

    private ChatToolRouterService.ToolDecision applyGuardrails(
            ChatToolRouterService.ToolDecision decision,
            String originalMessage,
            boolean strictIntentChecks
    ) {
        if (strictIntentChecks) {
            if (isUnexpectedReportDecision(decision, originalMessage)
                    || isUnexpectedS3Decision(decision, originalMessage)
                    || isUnexpectedAuditDecision(decision, originalMessage)) {
                return ChatToolRouterService.ToolDecision.none();
            }
        }

        if (decision.type() == ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT) {
            if (decision.bucket() == null || !looksLikeBucket(decision.bucket())) {
                return new ChatToolRouterService.ToolDecision(
                        ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT,
                        null,
                        null,
                        decision.region(),
                        decision.days(),
                        decision.reason(),
                        decision.services(),
                        clarificationFor(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, List.of("bucket"))
                );
            }
        }

        if (decision.type() == ChatToolRouterService.DecisionType.READ_LATEST_REPORT) {
            if (decision.reportType() == null || "all".equals(decision.reportType())) {
                return new ChatToolRouterService.ToolDecision(
                        ChatToolRouterService.DecisionType.READ_LATEST_REPORT,
                        null,
                        null,
                        null,
                        null,
                        decision.reason(),
                        decision.services(),
                        clarificationFor(ChatToolRouterService.DecisionType.READ_LATEST_REPORT, List.of("reportType"))
                );
            }
        }

        if (decision.type() == ChatToolRouterService.DecisionType.LIST_REPORTS) {
            String normalizedReportType = decision.reportType() == null ? "all" : decision.reportType();
            return new ChatToolRouterService.ToolDecision(
                    ChatToolRouterService.DecisionType.LIST_REPORTS,
                    normalizedReportType,
                    null,
                    null,
                    null,
                    decision.reason(),
                    List.of(),
                    decision.clarification()
            );
        }

        return decision;
    }

    private boolean looksLikeBucket(String bucket) {
        if (bucket == null) {
            return false;
        }
        return BUCKET_PATTERN.matcher(bucket.trim().toLowerCase(Locale.ROOT)).matches();
    }

    private boolean isUnexpectedReportDecision(ChatToolRouterService.ToolDecision decision, String originalMessage) {
        if (originalMessage == null) {
            return false;
        }
        if (decision.type() != ChatToolRouterService.DecisionType.LIST_REPORTS
                && decision.type() != ChatToolRouterService.DecisionType.READ_LATEST_REPORT) {
            return false;
        }

        String normalizedMessage = originalMessage.toLowerCase(Locale.ROOT);
        boolean mentionsReport = normalizedMessage.contains("report");
        boolean mentionsReportIntent = normalizedMessage.contains("read")
                || normalizedMessage.contains("show")
                || normalizedMessage.contains("open")
                || normalizedMessage.contains("latest")
                || normalizedMessage.contains("recent")
                || normalizedMessage.contains("list");
        return !(mentionsReport && mentionsReportIntent);
    }

    private boolean isUnexpectedS3Decision(ChatToolRouterService.ToolDecision decision, String originalMessage) {
        if (originalMessage == null || decision.type() != ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT) {
            return false;
        }

        String normalizedMessage = originalMessage.toLowerCase(Locale.ROOT);
        boolean mentionsBucket = normalizedMessage.contains("bucket");
        boolean mentionsMetrics = normalizedMessage.contains("metric") || normalizedMessage.contains("metrics");
        boolean mentionsCloudwatch = normalizedMessage.contains("cloudwatch");
        boolean mentionsReport = normalizedMessage.contains("report");

        return !(mentionsCloudwatch || mentionsMetrics || (mentionsBucket && mentionsReport));
    }

    private boolean isUnexpectedAuditDecision(ChatToolRouterService.ToolDecision decision, String originalMessage) {
        if (originalMessage == null || decision.type() != ChatToolRouterService.DecisionType.AWS_REGION_AUDIT) {
            return false;
        }

        String normalizedMessage = originalMessage.toLowerCase(Locale.ROOT);
        return !normalizedMessage.contains("audit");
    }

    public record PlanningResult(
            String rawResponse,
            Optional<ChatToolRouterService.ToolDecision> parsedDecision
    ) {
    }
}
