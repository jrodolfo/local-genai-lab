package net.jrodolfo.llm.service;

import net.jrodolfo.llm.model.PendingToolCall;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for routing user messages to appropriate tools based on intent and extraction.
 */
@Service
public class ChatToolRouterService {

    private static final Pattern REGION_PATTERN = Pattern.compile("\\b(af|ap|ca|eu|il|me|sa|us)-[a-z]+-\\d\\b");
    private static final Pattern DAYS_PATTERN = Pattern.compile("\\b(\\d{1,3})\\s+days?\\b");
    private static final Pattern BUCKET_AFTER_KEYWORD_PATTERN = Pattern.compile("\\bbucket(?:\\s+name)?\\s+(?:is\\s+)?([a-z0-9][a-z0-9.-]{1,253}[a-z0-9])\\b");
    private static final Pattern BUCKET_BEFORE_KEYWORD_PATTERN = Pattern.compile("\\b([a-z0-9][a-z0-9.-]{1,253}[a-z0-9])\\s+bucket\\b");
    private static final Pattern DOMAIN_LIKE_PATTERN = Pattern.compile("\\b([a-z0-9][a-z0-9.-]{1,253}[a-z0-9])\\b");
    private static final Set<String> BUCKET_STOP_WORDS = Set.of(
            "bucket", "buckets", "metric", "metrics", "report", "reports", "name",
            "the", "a", "an", "all", "latest", "recent", "cloudwatch", "s3"
    );
    private static final String S3_BUCKET_CLARIFICATION = "I can run an S3 CloudWatch report using your local AWS CLI credentials. "
            + "The current report tool runs one bucket at a time. Please provide the bucket name. "
            + "If you are not sure which buckets are available, ask me: \"list my S3 buckets\".";
    private static final String S3_ALL_BUCKETS_NOT_IMPLEMENTED = "All-bucket S3 CloudWatch reports are not implemented yet. "
            + "Please provide one bucket name. If you are not sure which buckets are available, ask me: \"list my S3 buckets\".";
    private static final List<String> AUDIT_SERVICES = List.of(
            "sts", "aws-config", "s3", "ec2", "elbv2", "rds", "lambda",
            "ecs", "eks", "sagemaker", "opensearch", "secretsmanager", "logs", "tagging"
    );
    private static final List<ServiceAlias> SERVICE_ALIASES = List.of(
            new ServiceAlias("aws-config", List.of("aws-config", "aws config", "cli config")),
            new ServiceAlias("sts", List.of("sts", "caller identity")),
            new ServiceAlias("s3", List.of("s3", "buckets")),
            new ServiceAlias("ec2", List.of("ec2", "instances", "ebs", "elastic ip", "elastic ips", "vpc", "vpcs", "subnets", "security groups")),
            new ServiceAlias("elbv2", List.of("elbv2", "load balancer", "load balancers")),
            new ServiceAlias("rds", List.of("rds")),
            new ServiceAlias("lambda", List.of("lambda")),
            new ServiceAlias("ecs", List.of("ecs")),
            new ServiceAlias("eks", List.of("eks")),
            new ServiceAlias("sagemaker", List.of("sagemaker", "sage maker")),
            new ServiceAlias("opensearch", List.of("opensearch", "open search")),
            new ServiceAlias("secretsmanager", List.of("secretsmanager", "secrets manager", "secret", "secrets")),
            new ServiceAlias("logs", List.of("cloudwatch logs", "log groups", "logs")),
            new ServiceAlias("tagging", List.of("tagging", "tagged resources"))
    );

    /**
     * Routes a message to a tool decision.
     *
     * @param message the user message
     * @return the tool decision
     */
    public ToolDecision route(String message) {
        String normalized = message.toLowerCase(Locale.ROOT).trim();

        ToolDecision reportReadDecision = matchReportRead(normalized);
        if (reportReadDecision != null) {
            return reportReadDecision;
        }

        ToolDecision reportListDecision = matchReportListing(normalized);
        if (reportListDecision != null) {
            return reportListDecision;
        }

        ToolDecision s3Decision = matchS3Cloudwatch(normalized, message);
        if (s3Decision != null) {
            return s3Decision;
        }

        ToolDecision auditDecision = matchAwsAudit(normalized);
        if (auditDecision != null) {
            return auditDecision;
        }

        return ToolDecision.none();
    }

    /**
     * Resolves a pending tool call based on a new user message.
     *
     * @param pendingToolCall the pending tool call
     * @param message         the new user message
     * @return the tool decision
     */
    public ToolDecision resolvePending(PendingToolCall pendingToolCall, String message) {
        String normalized = message.toLowerCase(Locale.ROOT).trim();

        return switch (pendingToolCall.type()) {
            case S3_CLOUDWATCH_REPORT -> resolvePendingS3(normalized, message, pendingToolCall);
            case READ_LATEST_REPORT -> resolvePendingLatestReport(normalized, pendingToolCall);
            default -> ToolDecision.none();
        };
    }

    /**
     * Matches report read intent.
     *
     * @param normalized the normalized message
     * @return the tool decision or null
     */
    private ToolDecision matchReportRead(String normalized) {
        boolean mentionsRead = normalized.contains("read")
                || normalized.contains("show")
                || normalized.contains("open");
        boolean mentionsLatest = normalized.contains("latest")
                || normalized.contains("most recent");
        boolean mentionsReport = normalized.contains("report");

        if (!mentionsReport || !(mentionsRead || mentionsLatest)) {
            return null;
        }

        String reportType = inferReportType(normalized);
        if ("all".equals(reportType)) {
            return ToolDecision.clarification(
                    DecisionType.READ_LATEST_REPORT,
                    "I can read the latest report, but I need to know whether you want the latest audit report or the latest s3 cloudwatch report."
            );
        }

        return new ToolDecision(
                DecisionType.READ_LATEST_REPORT,
                reportType,
                null,
                null,
                null,
                "latest report lookup"
        );
    }

    /**
     * Matches report listing intent.
     *
     * @param normalized the normalized message
     * @return the tool decision or null
     */
    private ToolDecision matchReportListing(String normalized) {
        boolean mentionsReports = normalized.contains("reports");
        boolean mentionsListIntent = normalized.contains("list")
                || normalized.contains("show")
                || normalized.contains("recent")
                || normalized.contains("latest");

        if (!mentionsReports || !mentionsListIntent) {
            return null;
        }

        return new ToolDecision(
                DecisionType.LIST_REPORTS,
                inferReportType(normalized),
                null,
                null,
                null,
                "recent report lookup"
        );
    }

    /**
     * Matches S3 CloudWatch report intent.
     *
     * @param normalized      the normalized message
     * @param originalMessage the original message
     * @return the tool decision or null
     */
    private ToolDecision matchS3Cloudwatch(String normalized, String originalMessage) {
        boolean mentionsS3Intent = normalized.contains("s3 cloudwatch")
                || normalized.contains("bucket metrics")
                || (normalized.contains("bucket") && normalized.contains("metrics"))
                || normalized.contains("bucket report")
                || normalized.contains("cloudwatch report for bucket")
                || normalized.contains("cloudwatch metrics for bucket")
                || (normalized.contains("s3") && (normalized.contains("report") || normalized.contains("usage")));

        if (!mentionsS3Intent) {
            return null;
        }

        String bucket = extractBucket(originalMessage);
        if (bucket == null) {
            return ToolDecision.clarification(
                    DecisionType.S3_CLOUDWATCH_REPORT,
                    null,
                    null,
                    extractRegion(normalized),
                    extractDays(normalized),
                    "s3 report request",
                    S3_BUCKET_CLARIFICATION
            );
        }

        return new ToolDecision(
                DecisionType.S3_CLOUDWATCH_REPORT,
                null,
                bucket,
                extractRegion(normalized),
                extractDays(normalized),
                "s3 cloudwatch metrics request"
        );
    }

    /**
     * Matches AWS audit intent.
     *
     * @param normalized the normalized message
     * @return the tool decision or null
     */
    private ToolDecision matchAwsAudit(String normalized) {
        if (mentionsS3BucketListing(normalized)) {
            return new ToolDecision(
                    DecisionType.AWS_REGION_AUDIT,
                    null,
                    null,
                    null,
                    null,
                    "s3 bucket listing request",
                    List.of("s3")
            );
        }

        boolean mentionsAudit = normalized.contains("aws audit")
                || normalized.contains("run audit")
                || normalized.contains("audit aws")
                || normalized.contains("region audit")
                || normalized.contains("audit my aws")
                || (normalized.contains("audit") && !normalized.contains("report") && (extractRegion(normalized) != null || !extractServices(normalized).isEmpty()));

        if (!mentionsAudit) {
            return null;
        }

        return new ToolDecision(
                DecisionType.AWS_REGION_AUDIT,
                null,
                null,
                extractRegion(normalized),
                null,
                "aws audit request",
                extractServices(normalized)
        );
    }

    /**
     * Matches natural user prompts that ask to list accessible S3 buckets.
     *
     * @param normalized the normalized message
     * @return true if the user asked to list S3 buckets
     */
    private boolean mentionsS3BucketListing(String normalized) {
        boolean mentionsS3Buckets = normalized.contains("s3 bucket")
                || normalized.contains("s3 buckets")
                || normalized.contains("buckets in s3")
                || normalized.contains("bucket names");
        boolean mentionsListIntent = normalized.contains("list")
                || normalized.contains("show")
                || normalized.contains("which")
                || normalized.contains("what");

        return mentionsS3Buckets && mentionsListIntent;
    }

    /**
     * Resolves a pending S3 report request.
     *
     * @param normalized      the normalized message
     * @param originalMessage the original message
     * @param pendingToolCall the pending tool call
     * @return the tool decision
     */
    private ToolDecision resolvePendingS3(String normalized, String originalMessage, PendingToolCall pendingToolCall) {
        String bucket = extractBucket(originalMessage);
        if (bucket == null) {
            if (looksLikeTopicChange(normalized)) {
                return ToolDecision.none();
            }
            if (wantsAllBuckets(normalized)) {
                return ToolDecision.clarification(
                        DecisionType.S3_CLOUDWATCH_REPORT,
                        null,
                        null,
                        pendingToolCall.region(),
                        pendingToolCall.days(),
                        pendingToolCall.reason() != null ? pendingToolCall.reason() : "s3 report request",
                        S3_ALL_BUCKETS_NOT_IMPLEMENTED
                );
            }
            return ToolDecision.clarification(
                    DecisionType.S3_CLOUDWATCH_REPORT,
                    null,
                    null,
                    pendingToolCall.region(),
                    pendingToolCall.days(),
                    pendingToolCall.reason() != null ? pendingToolCall.reason() : "s3 report request",
                    "I still need one S3 bucket name to run the S3 CloudWatch report."
            );
        }

        return new ToolDecision(
                DecisionType.S3_CLOUDWATCH_REPORT,
                null,
                bucket,
                extractRegion(normalized) != null ? extractRegion(normalized) : pendingToolCall.region(),
                extractDays(normalized) != null ? extractDays(normalized) : pendingToolCall.days(),
                pendingToolCall.reason() != null ? pendingToolCall.reason() : "s3 cloudwatch metrics request",
                pendingToolCall.services()
        );
    }

    /**
     * Resolves a pending latest report request.
     *
     * @param normalized      the normalized message
     * @param pendingToolCall the pending tool call
     * @return the tool decision
     */
    private ToolDecision resolvePendingLatestReport(String normalized, PendingToolCall pendingToolCall) {
        String reportType = inferReportType(normalized);
        if ("all".equals(reportType)) {
            if (looksLikeTopicChange(normalized)) {
                return ToolDecision.none();
            }
            return ToolDecision.clarification(
                    DecisionType.READ_LATEST_REPORT,
                    "I still need to know whether you want the latest audit report or the latest s3 cloudwatch report."
            );
        }

        return new ToolDecision(
                DecisionType.READ_LATEST_REPORT,
                reportType,
                null,
                null,
                null,
                pendingToolCall.reason() != null ? pendingToolCall.reason() : "latest report lookup"
        );
    }

    /**
     * Infers the report type from the message.
     *
     * @param normalized the normalized message
     * @return the inferred report type
     */
    private String inferReportType(String normalized) {
        if (normalized.contains("s3")) {
            return "s3_cloudwatch";
        }
        if (normalized.contains("audit")) {
            return "audit";
        }
        return "all";
    }

    /**
     * Extracts a bucket name from the message.
     *
     * @param message the message
     * @return the extracted bucket name or null
     */
    private String extractBucket(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);

        Matcher afterKeywordMatcher = BUCKET_AFTER_KEYWORD_PATTERN.matcher(normalized);
        if (afterKeywordMatcher.find()) {
            String candidate = afterKeywordMatcher.group(1);
            if (looksLikeBucketName(candidate)) {
                return candidate;
            }
        }

        Matcher beforeKeywordMatcher = BUCKET_BEFORE_KEYWORD_PATTERN.matcher(normalized);
        if (beforeKeywordMatcher.find()) {
            String candidate = beforeKeywordMatcher.group(1);
            if (looksLikeBucketName(candidate)) {
                return candidate;
            }
        }

        Matcher domainLikeMatcher = DOMAIN_LIKE_PATTERN.matcher(normalized);
        while (domainLikeMatcher.find()) {
            String candidate = domainLikeMatcher.group(1);
            if (looksLikeBucketName(candidate) && !candidate.equals(extractRegion(normalized))) {
                return candidate;
            }
        }

        return null;
    }

    /**
     * Extracts an AWS region from the message.
     *
     * @param normalized the normalized message
     * @return the extracted region or null
     */
    private String extractRegion(String normalized) {
        Matcher regionMatcher = REGION_PATTERN.matcher(normalized);
        if (regionMatcher.find()) {
            return regionMatcher.group();
        }
        return null;
    }

    /**
     * Extracts the number of days from the message.
     *
     * @param normalized the normalized message
     * @return the extracted days or null
     */
    private Integer extractDays(String normalized) {
        if (normalized.contains("last month") || normalized.contains("past month") || normalized.contains("previous month")) {
            return 30;
        }

        Matcher daysMatcher = DAYS_PATTERN.matcher(normalized);
        if (!daysMatcher.find()) {
            return null;
        }

        int days = Integer.parseInt(daysMatcher.group(1));
        return Math.min(days, 365);
    }

    /**
     * Checks if the user is asking for every accessible S3 bucket.
     *
     * @param normalized the normalized message
     * @return true if the user requested all buckets
     */
    private boolean wantsAllBuckets(String normalized) {
        return normalized.contains("all buckets")
                || normalized.contains("all accessible buckets")
                || normalized.contains("every bucket")
                || normalized.contains("each bucket");
    }

    /**
     * Extracts AWS services from the message.
     *
     * @param normalized the normalized message
     * @return a list of extracted services
     */
    private List<String> extractServices(String normalized) {
        Set<String> services = new LinkedHashSet<>();
        for (ServiceAlias alias : SERVICE_ALIASES) {
            for (String term : alias.aliases()) {
                if (normalized.contains(term)) {
                    services.add(alias.serviceKey());
                    break;
                }
            }
        }
        return new ArrayList<>(services);
    }

    /**
     * Validates if a string looks like a bucket name.
     *
     * @param candidate the string to validate
     * @return true if it looks like a bucket name, false otherwise
     */
    private boolean looksLikeBucketName(String candidate) {
        if (BUCKET_STOP_WORDS.contains(candidate)) {
            return false;
        }
        if (!candidate.contains(".")) {
            return candidate.contains("-") || candidate.matches(".*\\d.*");
        }
        if (candidate.startsWith(".") || candidate.endsWith(".")) {
            return false;
        }
        return candidate.length() >= 3 && candidate.length() <= 255;
    }

    /**
     * Checks if the user message indicates a change of topic.
     *
     * @param normalized the normalized message
     * @return true if it looks like a topic change, false otherwise
     */
    private boolean looksLikeTopicChange(String normalized) {
        return normalized.contains("explain")
                || normalized.contains("what is")
                || normalized.contains("how do")
                || normalized.contains("tell me")
                || normalized.contains("summarize")
                || normalized.contains("write ");
    }

    /**
     * Types of tool decisions.
     */
    public enum DecisionType {
        NONE,
        LIST_REPORTS,
        READ_LATEST_REPORT,
        AWS_REGION_AUDIT,
        S3_CLOUDWATCH_REPORT
    }

    /**
     * Record representing a tool routing decision.
     *
     * @param type          the type of decision
     * @param reportType    the report type, if applicable
     * @param bucket        the bucket name, if applicable
     * @param region        the AWS region, if applicable
     * @param days          the number of days, if applicable
     * @param reason        the reason for the decision
     * @param services      the list of AWS services, if applicable
     * @param clarification clarification text if needed
     */
    public record ToolDecision(
            DecisionType type,
            String reportType,
            String bucket,
            String region,
            Integer days,
            String reason,
            List<String> services,
            String clarification
    ) {
        /**
         * Constructor for a tool decision without services and clarification.
         *
         * @param type       the type of decision
         * @param reportType the report type
         * @param bucket     the bucket name
         * @param region     the AWS region
         * @param days       the number of days
         * @param reason     the reason for the decision
         */
        public ToolDecision(
                DecisionType type,
                String reportType,
                String bucket,
                String region,
                Integer days,
                String reason
        ) {
            this(type, reportType, bucket, region, days, reason, List.of(), null);
        }

        /**
         * Constructor for a tool decision with services but without clarification.
         *
         * @param type       the type of decision
         * @param reportType the report type
         * @param bucket     the bucket name
         * @param region     the AWS region
         * @param days       the number of days
         * @param reason     the reason for the decision
         * @param services   the list of AWS services
         */
        public ToolDecision(
                DecisionType type,
                String reportType,
                String bucket,
                String region,
                Integer days,
                String reason,
                List<String> services
        ) {
            this(type, reportType, bucket, region, days, reason, services, null);
        }

        /**
         * Creates a decision representing no tool match.
         *
         * @return a NONE tool decision
         */
        public static ToolDecision none() {
            return new ToolDecision(DecisionType.NONE, null, null, null, null, "no tool matched", List.of(), null);
        }

        /**
         * Creates a decision representing a need for clarification.
         *
         * @param type          the type of decision
         * @param clarification the clarification text
         * @return a tool decision with clarification
         */
        public static ToolDecision clarification(DecisionType type, String clarification) {
            return new ToolDecision(type, null, null, null, null, "clarification required", List.of(), clarification);
        }

        /**
         * Creates a clarification decision while preserving extracted tool arguments.
         *
         * @param type          the type of decision
         * @param reportType    the report type
         * @param bucket        the bucket name
         * @param region        the AWS region
         * @param days          the number of days
         * @param reason        the reason for the decision
         * @param clarification the clarification text
         * @return a tool decision with clarification and partial arguments
         */
        public static ToolDecision clarification(
                DecisionType type,
                String reportType,
                String bucket,
                String region,
                Integer days,
                String reason,
                String clarification
        ) {
            return new ToolDecision(type, reportType, bucket, region, days, reason, List.of(), clarification);
        }

        /**
         * Checks if a tool should be used based on this decision.
         *
         * @return true if a tool should be used, false otherwise
         */
        public boolean shouldUseTool() {
            return type != DecisionType.NONE && clarification == null;
        }

        /**
         * Checks if clarification is needed for this decision.
         *
         * @return true if clarification is needed, false otherwise
         */
        public boolean needsClarification() {
            return clarification != null && !clarification.isBlank();
        }
    }

    /**
     * Internal record to map service aliases to service keys.
     *
     * @param serviceKey the canonical service key
     * @param aliases    the list of aliases for the service
     */
    private record ServiceAlias(String serviceKey, List<String> aliases) {
    }
}
