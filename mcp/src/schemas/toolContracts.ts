import { z } from "zod";
import { REPORT_TYPES } from "./toolSchemas.js";

const reportTypeSchema = z.enum(REPORT_TYPES);
const concreteReportTypeSchema = z.enum(["audit", "s3_cloudwatch"]);

const jsonPrimitiveSchema = z.union([z.string(), z.number(), z.boolean(), z.null()]);

export const jsonValueSchema: z.ZodType<unknown> = z.lazy(() =>
  z.union([jsonPrimitiveSchema, z.array(jsonValueSchema), z.record(jsonValueSchema)]),
);

export const processRunResultSchema = z.object({
  command: z.string().min(1),
  args: z.array(z.string()),
  exitCode: z.number().int().nullable(),
  signal: z.string().nullable(),
  stdout: z.string(),
  stderr: z.string(),
  durationMs: z.number().int().nonnegative(),
  timedOut: z.boolean(),
}).strict();

export const reportSummarySchema = z.record(jsonValueSchema);

export const reportReferenceSchema = z.object({
  report_type: concreteReportTypeSchema,
  run_dir: z.string().min(1),
  directory_name: z.string().min(1),
  created_at: z.string().datetime({ offset: true }),
  report_txt: z.string().min(1),
  summary_json: z.string().min(1),
}).strict();

export const awsRegionAuditResultSchema = z.object({
  ok: z.boolean(),
  tool: z.literal("aws_region_audit"),
  report_type: z.literal("audit"),
  run_dir: z.string().min(1),
  execution: processRunResultSchema,
  summary: reportSummarySchema,
  report_preview: z.string(),
}).strict();

export const s3CloudwatchReportResultSchema = z.object({
  ok: z.boolean(),
  tool: z.literal("s3_cloudwatch_report"),
  report_type: z.literal("s3_cloudwatch"),
  run_dir: z.string().min(1),
  execution: processRunResultSchema,
  summary: reportSummarySchema,
  report_preview: z.string(),
}).strict();

export const listRecentReportsResultSchema = z.object({
  ok: z.literal(true),
  tool: z.literal("list_recent_reports"),
  report_type: reportTypeSchema,
  reports: z.array(reportReferenceSchema),
}).strict();

export const readReportSummaryResultSchema = z.object({
  ok: z.literal(true),
  tool: z.literal("read_report_summary"),
  report_type: concreteReportTypeSchema,
  run_dir: z.string().min(1),
  summary: reportSummarySchema,
  report_preview: z.string(),
}).strict();

export const toolErrorSchema = z.object({
  ok: z.literal(false),
  tool: z.string().min(1),
  error: z.string().min(1),
  details: jsonValueSchema.optional(),
}).strict();

export type AwsRegionAuditResult = z.infer<typeof awsRegionAuditResultSchema>;
export type S3CloudwatchReportResult = z.infer<typeof s3CloudwatchReportResultSchema>;
export type ListRecentReportsResult = z.infer<typeof listRecentReportsResultSchema>;
export type ReadReportSummaryResult = z.infer<typeof readReportSummaryResultSchema>;
export type ToolErrorResult = z.infer<typeof toolErrorSchema>;
