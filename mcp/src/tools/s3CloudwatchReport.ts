import type { S3CloudwatchReportInput } from "../schemas/toolSchemas.js";
import { s3CloudwatchReportResultSchema } from "../schemas/toolContracts.js";
import { config } from "../config.js";
import { runCommand } from "../services/processRunner.js";
import { detectNewRunDirectory, listReportDirectories } from "../services/reportLocator.js";
import { parseReportBundle } from "../services/reportParser.js";

function buildArgs(input: S3CloudwatchReportInput): string[] {
  const args = ["--bucket", input.bucket];

  if (input.region) {
    args.push("--region", input.region);
  }

  if (input.days) {
    args.push("--days", String(input.days));
  }

  return args;
}

export async function handleS3CloudwatchReport(input: S3CloudwatchReportInput) {
  const beforeRunDirectories = new Set(
    (await listReportDirectories("s3_cloudwatch")).map((directory) => directory.runDir),
  );

  const execution = await runCommand({
    command: "./aws-s3-cloudwatch-report.sh",
    args: buildArgs(input),
    cwd: config.scriptsDir,
    timeoutMs: config.s3TimeoutMs,
  });

  const runDir = await detectNewRunDirectory("s3_cloudwatch", beforeRunDirectories);
  if (!runDir) {
    throw new Error("S3 CloudWatch script finished but no report directory could be located.");
  }

  const parsedReport = await parseReportBundle(runDir);
  const summary = parsedReport.summary as Record<string, unknown>;

  return s3CloudwatchReportResultSchema.parse({
    ok: execution.exitCode === 0 && !execution.timedOut,
    tool: "s3_cloudwatch_report",
    report_type: "s3_cloudwatch",
    run_dir: parsedReport.runDir,
    execution,
    summary,
    report_preview: parsedReport.reportPreview,
  });
}
