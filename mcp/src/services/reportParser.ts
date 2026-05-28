import fs from "node:fs/promises";
import path from "node:path";
import {config} from "../config.js";
import {ensureWithinReports, type ReportType} from "./reportLocator.js";

type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

/**
 * Resolves a path relative to the scripts directory to an absolute path.
 *
 * @param value - The relative path to resolve.
 * @returns The absolute path.
 */
function resolveRepoRelativePath(value: string): string {
    return path.resolve(config.scriptsDir, value);
}

/**
 * Recursively scans a summary object and converts relative paths to absolute paths.
 * Paths are identified by keys ending in '_path' or equal to 'output_directory'.
 *
 * @param value - The JSON value to process.
 * @returns The processed JSON value with absolute paths.
 */
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

/**
 * Infers the report type based on its directory location.
 *
 * @param runDir - The directory path of the report.
 * @returns The inferred {@link ReportType}.
 * @throws {Error} If the report type cannot be inferred from the directory.
 */
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

/**
 * Parses a report bundle from a directory, including its summary and a preview of the full report.
 *
 * @param runDir - The directory containing the report bundle.
 * @param previewLines - The number of lines to include in the report preview (default: 20).
 * @returns A promise that resolves to the parsed report data.
 * @throws {Error} If the files cannot be read or the report type cannot be inferred.
 */
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
