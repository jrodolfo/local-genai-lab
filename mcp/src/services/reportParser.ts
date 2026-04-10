import fs from "node:fs/promises";
import path from "node:path";
import { config } from "../config.js";
import { ensureWithinReports, type ReportType } from "./reportLocator.js";

type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

function resolveRepoRelativePath(value: string): string {
  return path.resolve(config.scriptsDir, value);
}

function absolutizeSummaryPaths(value: JsonValue): JsonValue {
  if (Array.isArray(value)) {
    return value.map((item) => absolutizeSummaryPaths(item));
  }

  if (value && typeof value === "object") {
    const mappedEntries = Object.entries(value).map(([key, nestedValue]) => {
      if (
        typeof nestedValue === "string" &&
        (key.endsWith("_path") || key === "output_directory") &&
        !path.isAbsolute(nestedValue)
      ) {
        return [key, resolveRepoRelativePath(nestedValue)];
      }

      return [key, absolutizeSummaryPaths(nestedValue as JsonValue)];
    });

    return Object.fromEntries(mappedEntries);
  }

  return value;
}

export function inferReportType(runDir: string): ReportType {
  const normalizedRunDir = path.resolve(runDir);

  if (normalizedRunDir.startsWith(path.resolve(config.auditReportsDir) + path.sep)) {
    return "audit";
  }

  if (normalizedRunDir.startsWith(path.resolve(config.s3ReportsDir) + path.sep)) {
    return "s3_cloudwatch";
  }

  throw new Error(`Could not infer report type from run directory: ${runDir}`);
}

export async function parseReportBundle(runDir: string, previewLines = 20): Promise<{
  reportType: ReportType;
  runDir: string;
  summary: JsonValue;
  reportPreview: string;
}> {
  const safeRunDir = ensureWithinReports(runDir);
  const reportType = inferReportType(safeRunDir);
  const summaryPath = path.join(safeRunDir, "summary.json");
  const reportPath = path.join(safeRunDir, "report.txt");

  const [summaryContent, reportContent] = await Promise.all([
    fs.readFile(summaryPath, "utf8"),
    fs.readFile(reportPath, "utf8"),
  ]);

  const parsedSummary = JSON.parse(summaryContent) as JsonValue;
  const preview = reportContent.split("\n").slice(0, previewLines).join("\n").trim();

  return {
    reportType,
    runDir: safeRunDir,
    summary: absolutizeSummaryPaths(parsedSummary),
    reportPreview: preview,
  };
}
