import type {AwsRegionAuditInput} from "../schemas/toolSchemas.js";
import {awsRegionAuditResultSchema} from "../schemas/toolContracts.js";
import {config} from "../config.js";
import {runCommand} from "../services/processRunner.js";
import {detectNewRunDirectory, listReportDirectories} from "../services/reportLocator.js";
import {parseReportBundle} from "../services/reportParser.js";

/**
 * Builds the command line arguments for the AWS region audit script based on user input.
 *
 * @param input - The audit tool input parameters.
 * @returns An array of string arguments.
 */
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

/**
 * Handler for the AWS region audit tool.
 * Executes the `aws-region-audit-report.sh` script to audit AWS resources across specified regions and services.
 * Involved AWS services: multiple (depending on input), commonly includes STS, S3, EC2, RDS, Lambda, etc.
 *
 * @param input - Configuration for the audit including target regions and services.
 * @returns A promise that resolves to the audit result, including execution metadata, report summary, and preview.
 * @throws {Error} If the audit script fails to produce a report directory.
 */
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

    return awsRegionAuditResultSchema.parse({
        ok: execution.exitCode === 0 && !execution.timedOut,
        tool: "aws_region_audit",
        report_type: "audit",
        run_dir: parsedReport.runDir,
        execution,
        summary,
        report_preview: parsedReport.reportPreview,
    });
}
