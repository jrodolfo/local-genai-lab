import assert from "node:assert/strict";
import test from "node:test";
import {
  awsRegionAuditResultSchema,
  listRecentReportsResultSchema,
  readReportSummaryResultSchema,
  s3CloudwatchReportResultSchema,
  toolErrorSchema,
} from "./toolContracts.js";

const sampleExecution = {
  command: "./example.sh",
  args: ["--flag"],
  exitCode: 0,
  signal: null,
  stdout: "ok",
  stderr: "",
  durationMs: 1500,
  timedOut: false,
};

test("aws region audit result schema accepts a valid tool payload", () => {
  const parsed = awsRegionAuditResultSchema.parse({
    ok: true,
    tool: "aws_region_audit",
    report_type: "audit",
    run_dir: "/tmp/reports/audit/run-123",
    execution: sampleExecution,
    summary: {
      success_count: 37,
      failure_count: 0,
      selected_regions: ["us-east-1", "us-east-2"],
    },
    report_preview: "audit preview",
  });

  assert.equal(parsed.tool, "aws_region_audit");
});

test("s3 report schema accepts a valid tool payload", () => {
  const parsed = s3CloudwatchReportResultSchema.parse({
    ok: false,
    tool: "s3_cloudwatch_report",
    report_type: "s3_cloudwatch",
    run_dir: "/tmp/reports/s3-cloudwatch/run-123",
    execution: {
      ...sampleExecution,
      exitCode: 1,
      stderr: "partial failure",
    },
    summary: {
      bucket: "example.com",
      success_count: 2,
      failure_count: 1,
    },
    report_preview: "s3 preview",
  });

  assert.equal(parsed.ok, false);
});

test("list recent reports schema accepts a valid payload", () => {
  const parsed = listRecentReportsResultSchema.parse({
    ok: true,
    tool: "list_recent_reports",
    report_type: "all",
    reports: [
      {
        report_type: "audit",
        run_dir: "/tmp/reports/audit/run-123",
        directory_name: "run-123",
        created_at: "2026-04-17T10:00:00.000Z",
        report_txt: "/tmp/reports/audit/run-123/report.txt",
        summary_json: "/tmp/reports/audit/run-123/summary.json",
      },
    ],
  });

  assert.equal(parsed.reports.length, 1);
});

test("read report summary schema accepts a valid payload", () => {
  const parsed = readReportSummaryResultSchema.parse({
    ok: true,
    tool: "read_report_summary",
    report_type: "audit",
    run_dir: "/tmp/reports/audit/run-123",
    summary: {
      account_id: "123456789012",
      failed_steps: [],
    },
    report_preview: "summary preview",
  });

  assert.equal(parsed.report_type, "audit");
});

test("tool error schema requires tool and error message", () => {
  const parsed = toolErrorSchema.parse({
    ok: false,
    tool: "aws_region_audit",
    error: "script failed",
  });

  assert.equal(parsed.ok, false);
});

test("tool error schema rejects missing tool names", () => {
  assert.throws(() => {
    toolErrorSchema.parse({
      ok: false,
      tool: "",
      error: "missing tool name",
    });
  });
});
