import fs from "node:fs/promises";
import path from "node:path";
import {config} from "../config.js";

/**
 * Concrete report families written by the shell-based MCP tools.
 */
export type ReportType = "audit" | "s3_cloudwatch";

/**
 * Valid report bundle discovered under `agents/reports`.
 *
 * A directory is considered valid only when it contains both `summary.json`
 * and `report.txt`; incomplete script output is ignored by listing and lookup
 * helpers.
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
 * Describes the newest incomplete report directory, if one exists.
 *
 * @param reportType - The type of reports to inspect.
 * @param beforeRunDirectories - Directories that existed before the tool run.
 * @returns A diagnostic string, or null when no incomplete run is present.
 */
async function incompleteRunDiagnostic(
    reportType: ReportType,
    beforeRunDirectories: ReadonlySet<string>,
): Promise<string | null> {
    const baseDir = getReportsBaseDir(reportType);
    const entries = await fs.readdir(baseDir, {withFileTypes: true});

    const incomplete = await Promise.all(
        entries
            .filter((entry) => entry.isDirectory())
            .map(async (entry) => {
                const runDir = path.join(baseDir, entry.name);
                if (beforeRunDirectories.has(runDir) || await hasReportBundle(runDir)) {
                    return null;
                }
                const [reportExists, summaryExists, stats] = await Promise.all([
                    fileExists(path.join(runDir, "report.txt")),
                    fileExists(path.join(runDir, "summary.json")),
                    fs.stat(runDir),
                ]);
                return {
                    runDir,
                    reportExists,
                    summaryExists,
                    mtimeMs: stats.mtimeMs,
                };
            }),
    );

    const newest = incomplete
        .filter((directory): directory is NonNullable<typeof directory> => directory !== null)
        .sort((left, right) => right.mtimeMs - left.mtimeMs)[0];

    if (!newest) {
        return null;
    }

    const missing = [
        newest.reportExists ? null : "report.txt",
        newest.summaryExists ? null : "summary.json",
    ].filter(Boolean).join(", ");

    return `Incomplete report bundle found at ${newest.runDir}; missing ${missing}. ` +
        "The shell script may have failed before writing all artifacts, or jq may be missing from the backend runtime.";
}

/**
 * Checks whether a file exists.
 *
 * @param filePath - File path to check.
 * @returns True when the file exists.
 */
async function fileExists(filePath: string): Promise<boolean> {
    try {
        await fs.access(filePath);
        return true;
    } catch {
        return false;
    }
}

/**
 * Lists valid report directories for a specific report type.
 *
 * Only complete report bundles are returned, sorted by directory modification
 * time with newest first. This protects the backend from surfacing partially
 * written or manually created report folders.
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
 * Detects the report directory produced by a script invocation.
 *
 * The handler snapshots report directories before running a script and calls
 * this function afterward. If no new directory is found, the newest valid run
 * is returned as a fallback because some scripts may rewrite an existing run
 * directory in local development.
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
 * Detects the report directory produced by a script invocation or throws with a
 * diagnostic for incomplete bundles.
 *
 * @param reportType - The type of report to look for.
 * @param beforeRunDirectories - A set of directory paths that existed before the operation.
 * @param fallbackMessage - Message used when no complete or incomplete run can be found.
 * @returns A promise that resolves to the path of the detected report directory.
 */
export async function requireNewRunDirectory(
    reportType: ReportType,
    beforeRunDirectories: ReadonlySet<string>,
    fallbackMessage: string,
): Promise<string> {
    const afterRunDirectories = await listReportDirectories(reportType);
    const newDirectory = afterRunDirectories.find((directory) => !beforeRunDirectories.has(directory.runDir));
    if (newDirectory) {
        return newDirectory.runDir;
    }

    const diagnostic = await incompleteRunDiagnostic(reportType, beforeRunDirectories);
    if (diagnostic) {
        throw new Error(diagnostic);
    }

    const fallbackDirectory = afterRunDirectories[0];
    if (fallbackDirectory) {
        return fallbackDirectory.runDir;
    }

    throw new Error(fallbackMessage);
}

/**
 * Validates that a requested path stays inside the configured reports root.
 *
 * This is used before reading report summaries/previews so MCP callers cannot
 * use report-related tools as arbitrary filesystem readers.
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
