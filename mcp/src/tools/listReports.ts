import path from "node:path";
import type { ListRecentReportsInput } from "../schemas/toolSchemas.js";
import { listRecentReportsResultSchema } from "../schemas/toolContracts.js";
import { listReportDirectories, type ReportDirectory } from "../services/reportLocator.js";

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
