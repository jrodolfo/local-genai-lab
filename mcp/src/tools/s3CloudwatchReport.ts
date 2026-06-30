import type {S3CloudwatchReportInput} from "../schemas/toolSchemas.js";
import {s3CloudwatchReportResultSchema} from "../schemas/toolContracts.js";
import {config} from "../config.js";
import {runCommand} from "../services/processRunner.js";
import {detectNewRunDirectory, listReportDirectories} from "../services/reportLocator.js";
import {parseReportBundle} from "../services/reportParser.js";

/**
 * Builds the command line arguments for the S3 CloudWatch report script based on user input.
 *
 * @param input - The S3 CloudWatch report tool input parameters.
 * @returns An array of string arguments.
 */
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

/**
 * Handles the `s3_cloudwatch_report` MCP tool.
 *
 * The tool intentionally runs one bucket at a time. It delegates collection to
 * the repository shell script, then returns the generated report bundle in the
 * same structured shape used by the backend artifact and prompt flows.
 *
 * @param input - Configuration for the report, including bucket name, region, and time range.
 * @returns A promise that resolves to the report result, including execution metadata, report summary, and preview.
 * @throws {Error} If the report script fails to produce a report directory.
 */
export async function handleS3CloudwatchReport(input: S3CloudwatchReportInput) {
    const beforeRunDirectories = new Set(
        (await listReportDirectories("s3_cloudwatch")).map((directory) => directory.runDir),
    );

    const execution = await runCommand({
        command: "./aws-s3-cloudwatch-report.sh",
        args: buildArgs(input),
        cwd: config.agentsDir,
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
