import {z} from "zod";
import {REPORT_TYPES} from "./toolSchemas.js";

const reportTypeSchema = z.enum(REPORT_TYPES);
const concreteReportTypeSchema = z.enum(["audit", "s3_cloudwatch"]);

/**
 * Zod schema for JSON primitive values.
 */
const jsonPrimitiveSchema = z.union([z.string(), z.number(), z.boolean(), z.null()]);

/**
 * Zod schema for any valid JSON value.
 * Supports primitives, arrays, and objects (records).
 */
export const jsonValueSchema: z.ZodType<unknown> = z.lazy(() =>
    z.union([jsonPrimitiveSchema, z.array(jsonValueSchema), z.record(jsonValueSchema)]),
);

/**
 * Contract for shell-script execution metadata returned by report-producing tools.
 *
 * `ok` lives on the outer tool result; this nested object preserves the raw
 * process outcome so callers can inspect stdout/stderr and timeout state.
 */
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

/**
 * Zod schema for a report summary, which is a key-value record of JSON values.
 */
export const reportSummarySchema = z.record(jsonValueSchema);

/**
 * Contract for a discoverable report bundle.
 *
 * Paths are absolute backend-visible paths. The backend uses them to preview or
 * list generated artifacts from the Agent UI.
 */
export const reportReferenceSchema = z.object({
    report_type: concreteReportTypeSchema,
    run_dir: z.string().min(1),
    directory_name: z.string().min(1),
    created_at: z.string().datetime({offset: true}),
    report_txt: z.string().min(1),
    summary_json: z.string().min(1),
}).strict();

/**
 * Public structured result for the `aws_region_audit` MCP tool.
 */
export const awsRegionAuditResultSchema = z.object({
    ok: z.boolean(),
    tool: z.literal("aws_region_audit"),
    report_type: z.literal("audit"),
    run_dir: z.string().min(1),
    execution: processRunResultSchema,
    summary: reportSummarySchema,
    report_preview: z.string(),
}).strict();

/**
 * Public structured result for the one-bucket `s3_cloudwatch_report` MCP tool.
 */
export const s3CloudwatchReportResultSchema = z.object({
    ok: z.boolean(),
    tool: z.literal("s3_cloudwatch_report"),
    report_type: z.literal("s3_cloudwatch"),
    run_dir: z.string().min(1),
    execution: processRunResultSchema,
    summary: reportSummarySchema,
    report_preview: z.string(),
}).strict();

/**
 * Public structured result for listing existing report bundles without running AWS commands.
 */
export const listRecentReportsResultSchema = z.object({
    ok: z.literal(true),
    tool: z.literal("list_recent_reports"),
    report_type: reportTypeSchema,
    reports: z.array(reportReferenceSchema),
}).strict();

/**
 * Public structured result for reading an existing report bundle summary and preview.
 */
export const readReportSummaryResultSchema = z.object({
    ok: z.literal(true),
    tool: z.literal("read_report_summary"),
    report_type: concreteReportTypeSchema,
    run_dir: z.string().min(1),
    summary: reportSummarySchema,
    report_preview: z.string(),
}).strict();

/**
 * Zod schema for a standardized tool error response.
 */
export const toolErrorSchema = z.object({
    ok: z.literal(false),
    tool: z.string().min(1),
    error: z.string().min(1),
    details: jsonValueSchema.optional(),
}).strict();

/**
 * Type definition for AWS region audit tool results.
 */
export type AwsRegionAuditResult = z.infer<typeof awsRegionAuditResultSchema>;

/**
 * Type definition for S3 CloudWatch report tool results.
 */
export type S3CloudwatchReportResult = z.infer<typeof s3CloudwatchReportResultSchema>;

/**
 * Type definition for listing recent reports results.
 */
export type ListRecentReportsResult = z.infer<typeof listRecentReportsResultSchema>;

/**
 * Type definition for reading report summary results.
 */
export type ReadReportSummaryResult = z.infer<typeof readReportSummaryResultSchema>;

/**
 * Type definition for tool error results.
 */
export type ToolErrorResult = z.infer<typeof toolErrorSchema>;
