import fs from "node:fs/promises";
import path from "node:path";
import { config } from "../config.js";

export type ReportType = "audit" | "s3_cloudwatch";

export type ReportDirectory = {
  reportType: ReportType;
  runDir: string;
  directoryName: string;
  createdAt: string;
};

export function getReportsBaseDir(reportType: ReportType): string {
  return reportType === "audit" ? config.auditReportsDir : config.s3ReportsDir;
}

export async function listReportDirectories(reportType: ReportType): Promise<ReportDirectory[]> {
  const baseDir = getReportsBaseDir(reportType);
  const entries = await fs.readdir(baseDir, { withFileTypes: true });

  const directories = await Promise.all(
    entries
      .filter((entry) => entry.isDirectory())
      .map(async (entry) => {
        const runDir = path.join(baseDir, entry.name);
        const stats = await fs.stat(runDir);
        return {
          reportType,
          runDir,
          directoryName: entry.name,
          createdAt: stats.mtime.toISOString(),
          mtimeMs: stats.mtimeMs,
        };
      }),
  );

  return directories
    .sort((left, right) => right.mtimeMs - left.mtimeMs)
    .map(({ mtimeMs: _mtimeMs, ...directory }) => directory);
}

export async function detectNewRunDirectory(
  reportType: ReportType,
  beforeRunDirectories: ReadonlySet<string>,
): Promise<string | null> {
  const afterRunDirectories = await listReportDirectories(reportType);
  const newDirectory = afterRunDirectories.find((directory) => !beforeRunDirectories.has(directory.runDir));
  return newDirectory?.runDir ?? afterRunDirectories[0]?.runDir ?? null;
}

export function ensureWithinReports(requestedPath: string): string {
  const resolvedPath = path.resolve(requestedPath);
  const normalizedReportsDir = path.resolve(config.reportsDir) + path.sep;

  if (
    resolvedPath !== path.resolve(config.reportsDir) &&
    !resolvedPath.startsWith(normalizedReportsDir)
  ) {
    throw new Error(`Path is outside the allowed reports directory: ${requestedPath}`);
  }

  return resolvedPath;
}
