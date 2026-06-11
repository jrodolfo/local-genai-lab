import path from "node:path";
import type {ListRecentReportsInput} from "../schemas/toolSchemas.js";
import {listRecentReportsResultSchema} from "../schemas/toolContracts.js";
import {listReportDirectories, type ReportDirectory} from "../services/reportLocator.js";

/**
 * Converts an internal report directory into the public MCP response shape.
 *
 * The response includes direct paths to `report.txt` and `summary.json` so the
 * backend can offer artifact actions without re-discovering files.
 *
 * @param directory - The internal report directory object.
 * @returns An object conforming to the report reference schema.
 */
function toResponseShape(directory: ReportDirectory) {
    return {
        report_type: directory.reportType,
        run_dir: directory.runDir,
        directory_name: path.basename(directory.runDir),
        created_at: directory.createdAt,
        report_txt: path.join(directory.runDir, "report.txt"),
        summary_json: path.join(directory.runDir, "summary.json"),
    };
}

/**
 * Handles the `list_recent_reports` MCP tool.
 *
 * The tool reads already-generated report bundles only; it never starts AWS
 * scripts. `report_type=all` merges audit and S3 report directories and returns
 * the newest entries across both families.
 *
 * @param input - Configuration for listing reports, including report type and result limit.
 * @returns A promise that resolves to the list of recent reports.
 */
export async function handleListRecentReports(input: ListRecentReportsInput) {
    const collectedDirectories: ReportDirectory[] =
        input.report_type === "all"
            ? [
                ...(await listReportDirectories("audit")),
                ...(await listReportDirectories("s3_cloudwatch")),
            ]
            : await listReportDirectories(input.report_type);

    const reports = collectedDirectories
        .sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt))
        .slice(0, input.limit)
        .map(toResponseShape);

    return listRecentReportsResultSchema.parse({
        ok: true,
        tool: "list_recent_reports",
        report_type: input.report_type,
        reports,
    });
}
