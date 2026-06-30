import path from "node:path";
import {fileURLToPath} from "node:url";
import fs from "node:fs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const repoRoot = path.resolve(__dirname, "..", "..");
const agentsDir = path.join(repoRoot, "agents");
const reportsDir = path.join(agentsDir, "reports");
const auditReportsDir = path.join(reportsDir, "audit");
const s3ReportsDir = path.join(reportsDir, "s3-cloudwatch");

/**
 * Resolves a timeout value from an environment variable.
 *
 * @param name - The name of the environment variable.
 * @param fallback - The default value to return if the environment variable is missing or invalid.
 * @returns The resolved timeout value in milliseconds.
 */
function resolveTimeout(name: string, fallback: number): number {
    const rawValue = process.env[name];
    if (!rawValue) {
        return fallback;
    }

    const parsedValue = Number(rawValue);
    if (!Number.isFinite(parsedValue) || parsedValue <= 0) {
        return fallback;
    }

    return parsedValue;
}

/**
 * Asserts that a directory exists at the specified path.
 *
 * @param directoryPath - The filesystem path to check.
 * @throws {Error} If the directory does not exist.
 */
function assertDirectoryExists(directoryPath: string): void {
    if (!fs.existsSync(directoryPath)) {
        throw new Error(`Required directory is missing: ${directoryPath}`);
    }
}

assertDirectoryExists(agentsDir);
assertDirectoryExists(reportsDir);
assertDirectoryExists(auditReportsDir);
assertDirectoryExists(s3ReportsDir);

/**
 * Runtime configuration for the local MCP server.
 *
 * <p>The MCP tools execute shell scripts from {@link config.agentsDir} and
 * discover generated report bundles under {@link config.reportsDir}. Timeout
 * values are intentionally environment-driven so long-running local AWS audits
 * can be tuned without changing tool code.
 */
export const config = {
    repoRoot,
    agentsDir,
    reportsDir,
    auditReportsDir,
    s3ReportsDir,
    auditTimeoutMs: resolveTimeout("MCP_AUDIT_TIMEOUT_MS", 15 * 60 * 1000),
    s3TimeoutMs: resolveTimeout("MCP_S3_REPORT_TIMEOUT_MS", 15 * 60 * 1000),
} as const;
