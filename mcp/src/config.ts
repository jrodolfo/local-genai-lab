import path from "node:path";
import { fileURLToPath } from "node:url";
import fs from "node:fs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const repoRoot = path.resolve(__dirname, "..", "..");
const scriptsDir = path.join(repoRoot, "scripts");
const reportsDir = path.join(scriptsDir, "reports");
const auditReportsDir = path.join(reportsDir, "audit");
const s3ReportsDir = path.join(reportsDir, "s3-cloudwatch");

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

function assertDirectoryExists(directoryPath: string): void {
  if (!fs.existsSync(directoryPath)) {
    throw new Error(`Required directory is missing: ${directoryPath}`);
  }
}

assertDirectoryExists(scriptsDir);
assertDirectoryExists(reportsDir);
assertDirectoryExists(auditReportsDir);
assertDirectoryExists(s3ReportsDir);

export const config = {
  repoRoot,
  scriptsDir,
  reportsDir,
  auditReportsDir,
  s3ReportsDir,
  auditTimeoutMs: resolveTimeout("MCP_AUDIT_TIMEOUT_MS", 15 * 60 * 1000),
  s3TimeoutMs: resolveTimeout("MCP_S3_REPORT_TIMEOUT_MS", 15 * 60 * 1000),
} as const;
