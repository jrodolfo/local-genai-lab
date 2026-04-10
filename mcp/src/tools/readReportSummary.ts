import type { ReadReportSummaryInput } from "../schemas/toolSchemas.js";
import { parseReportBundle } from "../services/reportParser.js";

export async function handleReadReportSummary(input: ReadReportSummaryInput) {
  const parsedReport = await parseReportBundle(input.run_dir, input.preview_lines);

  return {
    ok: true,
    tool: "read_report_summary",
    report_type: parsedReport.reportType,
    run_dir: parsedReport.runDir,
    summary: parsedReport.summary,
    report_preview: parsedReport.reportPreview,
  };
}
