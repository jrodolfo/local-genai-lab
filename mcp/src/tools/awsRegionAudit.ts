import type {AwsRegionAuditInput} from "../schemas/toolSchemas.js";
import {awsRegionAuditResultSchema} from "../schemas/toolContracts.js";
import {config} from "../config.js";
import {runCommand} from "../services/processRunner.js";
import {listReportDirectories, requireNewRunDirectory} from "../services/reportLocator.js";
import {parseReportBundle} from "../services/reportParser.js";
import {assertAwsReportPrerequisites} from "../services/toolPrerequisites.js";

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
 * Handles the `aws_region_audit` MCP tool.
 *
 * The handler snapshots existing audit report directories, runs the repository
 * shell script, discovers the produced report bundle, parses its summary and
 * preview, then validates the final structured payload against the public tool
 * contract.
 *
 * @param input - Configuration for the audit including target regions and services.
 * @returns A promise that resolves to the audit result, including execution metadata, report summary, and preview.
 * @throws {Error} If the audit script fails to produce a report directory.
 */
export async function handleAwsRegionAudit(input: AwsRegionAuditInput) {
    await assertAwsReportPrerequisites("AWS region audit");

    const beforeRunDirectories = new Set(
        (await listReportDirectories("audit")).map((directory) => directory.runDir),
    );

    const execution = await runCommand({
        command: "./aws-region-audit-report.sh",
        args: buildArgs(input),
        cwd: config.agentsDir,
        timeoutMs: config.auditTimeoutMs,
    });

    const runDir = await requireNewRunDirectory(
        "audit",
        beforeRunDirectories,
        "Audit script finished but no complete report directory could be located.",
    );

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
