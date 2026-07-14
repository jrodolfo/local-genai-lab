import {constants} from "node:fs";
import fs from "node:fs/promises";
import path from "node:path";

/**
 * Checks whether a command is available on PATH.
 *
 * @param command - Command name to resolve.
 * @returns True when the command can be found.
 */
export async function commandExists(command: string): Promise<boolean> {
    const pathValue = process.env.PATH;

    if (!pathValue) {
        return false;
    }

    for (const directory of pathValue.split(path.delimiter).filter(Boolean)) {
        try {
            await fs.access(path.join(directory, command), constants.X_OK);
            return true;
        } catch {
            // Continue searching the remaining PATH entries.
        }
    }

    return false;
}

/**
 * Fails early when shell-backed AWS tools cannot produce valid report bundles.
 *
 * The audit scripts can produce a text report without jq, but the MCP contract
 * requires summary.json so the backend can show structured artifacts.
 */
export async function assertAwsReportPrerequisites(toolLabel: string): Promise<void> {
    const missing: string[] = [];

    if (!(await commandExists("aws"))) {
        missing.push("aws CLI");
    }
    if (!(await commandExists("jq"))) {
        missing.push("jq");
    }

    if (missing.length > 0) {
        throw new Error(
            `${toolLabel} requires ${missing.join(" and ")} in the backend runtime. ` +
            "In Docker mode these AWS tools are not supported by default; use host-run mode or configure a Docker image/runtime with AWS CLI, jq, and AWS credentials.",
        );
    }
}
