import {z} from "zod";

/**
 * AWS service filters accepted by the audit shell script and MCP schema.
 */
export const AUDIT_SERVICES = [
    "sts",
    "aws-config",
    "s3",
    "ec2",
    "elbv2",
    "rds",
    "lambda",
    "ecs",
    "eks",
    "sagemaker",
    "opensearch",
    "secretsmanager",
    "logs",
    "tagging",
] as const;

/**
 * Report type filters used by report-listing tools.
 *
 * - `audit`: AWS region audit reports.
 * - `s3_cloudwatch`: one-bucket S3 CloudWatch reports.
 * - `all`: both report families.
 */
export const REPORT_TYPES = ["audit", "s3_cloudwatch", "all"] as const;

/**
 * Input contract for `aws_region_audit`.
 *
 * Empty input means the shell script uses its default region/service coverage.
 */
export const awsRegionAuditSchema = z.object({
    regions: z.array(z.string().trim().min(1)).max(20).optional(),
    services: z.array(z.enum(AUDIT_SERVICES)).max(AUDIT_SERVICES.length).optional(),
});

/**
 * Input contract for `s3_cloudwatch_report`.
 *
 * `bucket` is required because the current script intentionally reports one
 * bucket at a time.
 */
export const s3CloudwatchReportSchema = z.object({
    bucket: z.string().trim().min(3),
    region: z.string().trim().min(1).optional(),
    days: z.number().int().positive().max(365).optional(),
});

/**
 * Input contract for `list_recent_reports`.
 */
export const listRecentReportsSchema = z.object({
    report_type: z.enum(REPORT_TYPES).default("all"),
    limit: z.number().int().positive().max(20).default(10),
});

/**
 * Input contract for `read_report_summary`.
 *
 * `run_dir` must resolve under `scripts/reports`; that containment check is
 * performed by the report parser before file reads.
 */
export const readReportSummarySchema = z.object({
    run_dir: z.string().trim().min(1),
    preview_lines: z.number().int().positive().max(80).default(20),
});

/**
 * Type definition for AWS region audit tool input.
 */
export type AwsRegionAuditInput = z.infer<typeof awsRegionAuditSchema>;

/**
 * Type definition for S3 CloudWatch report tool input.
 */
export type S3CloudwatchReportInput = z.infer<typeof s3CloudwatchReportSchema>;

/**
 * Type definition for listing recent reports tool input.
 */
export type ListRecentReportsInput = z.infer<typeof listRecentReportsSchema>;

/**
 * Type definition for reading report summary tool input.
 */
export type ReadReportSummaryInput = z.infer<typeof readReportSummarySchema>;
