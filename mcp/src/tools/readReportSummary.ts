import type {ReadReportSummaryInput} from "../schemas/toolSchemas.js";
import {readReportSummaryResultSchema} from "../schemas/toolContracts.js";
import {parseReportBundle} from "../services/reportParser.js";

/**
 * Handles the `read_report_summary` MCP tool.
 *
 * The handler reads an existing report bundle after validating that the
 * requested directory is inside the reports root. It is used when the user asks
 * to inspect a previously generated report without rerunning AWS commands.
 *
 * @param input - Configuration including the report directory and number of preview lines.
 * @returns A promise that resolves to the report summary and preview data.
 * @throws {Error} If the report directory is invalid or files are missing.
 */
export async function handleReadReportSummary(input: ReadReportSummaryInput) {
    const parsedReport = await parseReportBundle(input.run_dir, input.preview_lines);

    return readReportSummaryResultSchema.parse({
        ok: true,
        tool: "read_report_summary",
        report_type: parsedReport.reportType,
        run_dir: parsedReport.runDir,
        summary: parsedReport.summary,
        report_preview: parsedReport.reportPreview,
    });
}
