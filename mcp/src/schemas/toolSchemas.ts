import {z} from "zod";

/**
 * List of AWS services supported by the audit tool.
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
 * Supported report types.
 * - audit: AWS region audit reports.
 * - s3_cloudwatch: S3 CloudWatch usage reports.
 * - all: Both types of reports.
 */
export const REPORT_TYPES = ["audit", "s3_cloudwatch", "all"] as const;

/**
 * Zod schema for the AWS region audit tool input parameters.
 */
export const awsRegionAuditSchema = z.object({
    regions: z.array(z.string().trim().min(1)).max(20).optional(),
    services: z.array(z.enum(AUDIT_SERVICES)).max(AUDIT_SERVICES.length).optional(),
});

/**
 * Zod schema for the S3 CloudWatch report tool input parameters.
 */
export const s3CloudwatchReportSchema = z.object({
    bucket: z.string().trim().min(3),
    region: z.string().trim().min(1).optional(),
    days: z.number().int().positive().max(365).optional(),
});

/**
 * Zod schema for listing recent reports tool input parameters.
 */
export const listRecentReportsSchema = z.object({
    report_type: z.enum(REPORT_TYPES).default("all"),
    limit: z.number().int().positive().max(20).default(10),
});

/**
 * Zod schema for reading a report summary tool input parameters.
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
