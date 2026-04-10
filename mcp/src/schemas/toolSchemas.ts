import { z } from "zod";

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

export const REPORT_TYPES = ["audit", "s3_cloudwatch", "all"] as const;

export const awsRegionAuditSchema = z.object({
  regions: z.array(z.string().trim().min(1)).max(20).optional(),
  services: z.array(z.enum(AUDIT_SERVICES)).max(AUDIT_SERVICES.length).optional(),
});

export const s3CloudwatchReportSchema = z.object({
  bucket: z.string().trim().min(3),
  region: z.string().trim().min(1).optional(),
  days: z.number().int().positive().max(365).optional(),
});

export const listRecentReportsSchema = z.object({
  report_type: z.enum(REPORT_TYPES).default("all"),
  limit: z.number().int().positive().max(20).default(10),
});

export const readReportSummarySchema = z.object({
  run_dir: z.string().trim().min(1),
  preview_lines: z.number().int().positive().max(80).default(20),
});

export type AwsRegionAuditInput = z.infer<typeof awsRegionAuditSchema>;
export type S3CloudwatchReportInput = z.infer<typeof s3CloudwatchReportSchema>;
export type ListRecentReportsInput = z.infer<typeof listRecentReportsSchema>;
export type ReadReportSummaryInput = z.infer<typeof readReportSummarySchema>;
