package net.jrodolfo.llm.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatToolRouterService {

    private static final Pattern REGION_PATTERN = Pattern.compile("\\b(af|ap|ca|eu|il|me|sa|us)-[a-z]+-\\d\\b");
    private static final Pattern DAYS_PATTERN = Pattern.compile("\\b(\\d{1,3})\\s+days?\\b");
    private static final Pattern BUCKET_AFTER_KEYWORD_PATTERN = Pattern.compile("\\bbucket(?:\\s+name)?\\s+(?:is\\s+)?([a-z0-9][a-z0-9.-]{1,253}[a-z0-9])\\b");
    private static final Pattern BUCKET_BEFORE_KEYWORD_PATTERN = Pattern.compile("\\b([a-z0-9][a-z0-9.-]{1,253}[a-z0-9])\\s+bucket\\b");
    private static final Pattern DOMAIN_LIKE_PATTERN = Pattern.compile("\\b([a-z0-9][a-z0-9.-]{1,253}[a-z0-9])\\b");
    private static final Set<String> BUCKET_STOP_WORDS = Set.of(
            "bucket", "buckets", "metric", "metrics", "report", "reports", "name",
            "the", "a", "an", "latest", "recent", "cloudwatch", "s3"
    );
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

    private ToolDecision matchS3Cloudwatch(String normalized, String originalMessage) {
        boolean mentionsS3Intent = normalized.contains("s3 cloudwatch")
                || normalized.contains("bucket metrics")
                || (normalized.contains("bucket") && normalized.contains("metrics"))
                || normalized.contains("bucket report")
                || normalized.contains("cloudwatch report for bucket")
                || normalized.contains("cloudwatch metrics for bucket");

        if (!mentionsS3Intent) {
            return null;
        }

        String bucket = extractBucket(originalMessage);
        if (bucket == null) {
            return ToolDecision.clarification(
                    DecisionType.S3_CLOUDWATCH_REPORT,
                    "I can run the S3 CloudWatch report, but I need the bucket name."
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

    private ToolDecision matchAwsAudit(String normalized) {
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

    private String inferReportType(String normalized) {
        if (normalized.contains("s3")) {
            return "s3_cloudwatch";
        }
        if (normalized.contains("audit")) {
            return "audit";
        }
        return "all";
    }

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

    private String extractRegion(String normalized) {
        Matcher regionMatcher = REGION_PATTERN.matcher(normalized);
        if (regionMatcher.find()) {
            return regionMatcher.group();
        }
        return null;
    }

    private Integer extractDays(String normalized) {
        Matcher daysMatcher = DAYS_PATTERN.matcher(normalized);
        if (!daysMatcher.find()) {
            return null;
        }

        int days = Integer.parseInt(daysMatcher.group(1));
        return Math.min(days, 365);
    }

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

    public enum DecisionType {
        NONE,
        LIST_REPORTS,
        READ_LATEST_REPORT,
        AWS_REGION_AUDIT,
        S3_CLOUDWATCH_REPORT
    }

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

        public static ToolDecision none() {
            return new ToolDecision(DecisionType.NONE, null, null, null, null, "no tool matched", List.of(), null);
        }

        public static ToolDecision clarification(DecisionType type, String clarification) {
            return new ToolDecision(type, null, null, null, null, "clarification required", List.of(), clarification);
        }

        public boolean shouldUseTool() {
            return type != DecisionType.NONE && clarification == null;
        }

        public boolean needsClarification() {
            return clarification != null && !clarification.isBlank();
        }
    }

    private record ServiceAlias(String serviceKey, List<String> aliases) {
    }
}
