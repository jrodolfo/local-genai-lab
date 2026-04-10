import type { AwsRegionAuditInput } from "../schemas/toolSchemas.js";
import { config } from "../config.js";
import { runCommand } from "../services/processRunner.js";
import { detectNewRunDirectory, listReportDirectories } from "../services/reportLocator.js";
import { parseReportBundle } from "../services/reportParser.js";

function buildArgs(input: AwsRegionAuditInput): string[] {
  const args: string[] = [];

  if (input.regions && input.regions.length > 0) {
    args.push("--regions", ...input.regions);
  }

  if (input.services && input.services.length > 0) {
    args.push("--services", ...input.services);
  }

  return args;
}

export async function handleAwsRegionAudit(input: AwsRegionAuditInput) {
  const beforeRunDirectories = new Set(
    (await listReportDirectories("audit")).map((directory) => directory.runDir),
  );

  const execution = await runCommand({
    command: "./aws-region-audit-report.sh",
    args: buildArgs(input),
    cwd: config.scriptsDir,
    timeoutMs: config.auditTimeoutMs,
  });

  const runDir = await detectNewRunDirectory("audit", beforeRunDirectories);
  if (!runDir) {
    throw new Error("Audit script finished but no report directory could be located.");
  }

  const parsedReport = await parseReportBundle(runDir);
  const summary = parsedReport.summary as Record<string, unknown>;

  return {
    ok: execution.exitCode === 0 && !execution.timedOut,
    tool: "aws_region_audit",
    report_type: "audit",
    run_dir: parsedReport.runDir,
    execution,
    summary,
    report_preview: parsedReport.reportPreview,
  };
}
