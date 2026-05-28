import fs from "node:fs/promises";
import path from "node:path";
import {config} from "../config.js";

/**
 * Supported report types.
 */
export type ReportType = "audit" | "s3_cloudwatch";

/**
 * Information about a report directory.
 */
export type ReportDirectory = {
    /** The type of report stored in the directory. */
    reportType: ReportType;
    /** The full filesystem path to the report directory. */
    runDir: string;
    /** The name of the directory. */
    directoryName: string;
    /** The ISO timestamp when the report was created (based on directory mtime). */
    createdAt: string;
};

/**
 * Gets the base directory where reports of a specific type are stored.
 *
 * @param reportType - The type of report.
 * @returns The absolute path to the base directory.
 */
export function getReportsBaseDir(reportType: ReportType): string {
    return reportType === "audit" ? config.auditReportsDir : config.s3ReportsDir;
}

/**
 * Checks if a directory contains a complete report bundle.
 * A complete bundle must contain both 'summary.json' and 'report.txt'.
 *
 * @param runDir - The directory to check.
 * @returns A promise that resolves to true if the bundle is complete, false otherwise.
 */
async function hasReportBundle(runDir: string): Promise<boolean> {
    try {
        await Promise.all([
            fs.access(path.join(runDir, "summary.json")),
            fs.access(path.join(runDir, "report.txt")),
        ]);
        return true;
    } catch {
        return false;
    }
}

/**
 * Lists all valid report directories for a specific report type, sorted by creation time (newest first).
 *
 * @param reportType - The type of reports to list.
 * @returns A promise that resolves to an array of {@link ReportDirectory} objects.
 */
export async function listReportDirectories(reportType: ReportType): Promise<ReportDirectory[]> {
    const baseDir = getReportsBaseDir(reportType);
    const entries = await fs.readdir(baseDir, {withFileTypes: true});

    const directories = await Promise.all(
        entries
            .filter((entry) => entry.isDirectory())
            .map(async (entry) => {
                const runDir = path.join(baseDir, entry.name);
                if (!(await hasReportBundle(runDir))) {
                    return null;
                }
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
        .filter((directory): directory is NonNullable<typeof directory> => directory !== null)
        .sort((left, right) => right.mtimeMs - left.mtimeMs)
        .map(({mtimeMs: _mtimeMs, ...directory}) => directory);
}

/**
 * Detects a newly created run directory by comparing directories before and after an operation.
 *
 * @param reportType - The type of report to look for.
 * @param beforeRunDirectories - A set of directory paths that existed before the operation.
 * @returns A promise that resolves to the path of the new directory, or null if not found.
 */
export async function detectNewRunDirectory(
    reportType: ReportType,
    beforeRunDirectories: ReadonlySet<string>,
): Promise<string | null> {
    const afterRunDirectories = await listReportDirectories(reportType);
    const newDirectory = afterRunDirectories.find((directory) => !beforeRunDirectories.has(directory.runDir));
    return newDirectory?.runDir ?? afterRunDirectories[0]?.runDir ?? null;
}

/**
 * Validates that a requested path is within the allowed reports directory.
 * Prevents directory traversal attacks.
 *
 * @param requestedPath - The path to validate.
 * @returns The resolved absolute path if it's within the allowed directory.
 * @throws {Error} If the path is outside the allowed reports directory.
 */
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
