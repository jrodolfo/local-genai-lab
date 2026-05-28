import type {ReadReportSummaryInput} from "../schemas/toolSchemas.js";
import {readReportSummaryResultSchema} from "../schemas/toolContracts.js";
import {parseReportBundle} from "../services/reportParser.js";

/**
 * Handler for the read report summary tool.
 * Reads and parses the summary and preview of an existing report from a specified directory.
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
