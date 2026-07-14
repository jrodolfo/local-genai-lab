import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test, {afterEach} from "node:test";
import {assertAwsReportPrerequisites} from "./toolPrerequisites.js";

const originalPath = process.env.PATH;

afterEach(async () => {
    process.env.PATH = originalPath;
});

async function writeExecutable(directory: string, name: string) {
    const filePath = path.join(directory, name);
    await fs.writeFile(filePath, "#!/usr/bin/env sh\nexit 0\n");
    await fs.chmod(filePath, 0o755);
}

test("aws report prerequisite check fails clearly when aws cli is missing", async () => {
    const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "local-genai-lab-mcp-path-"));

    try {
        await writeExecutable(tempRoot, "jq");
        process.env.PATH = tempRoot;

        await assert.rejects(
            () => assertAwsReportPrerequisites("AWS region audit"),
            /AWS region audit requires aws CLI in the backend runtime/,
        );
    } finally {
        await fs.rm(tempRoot, {recursive: true, force: true});
    }
});

test("aws report prerequisite check fails clearly when jq is missing", async () => {
    const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "local-genai-lab-mcp-path-"));

    try {
        await writeExecutable(tempRoot, "aws");
        process.env.PATH = tempRoot;

        await assert.rejects(
            () => assertAwsReportPrerequisites("S3 CloudWatch report"),
            /S3 CloudWatch report requires jq in the backend runtime/,
        );
    } finally {
        await fs.rm(tempRoot, {recursive: true, force: true});
    }
});

test("aws report prerequisite check passes when aws cli and jq are available", async () => {
    const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "local-genai-lab-mcp-path-"));

    try {
        await writeExecutable(tempRoot, "aws");
        await writeExecutable(tempRoot, "jq");
        process.env.PATH = tempRoot;

        await assert.doesNotReject(() => assertAwsReportPrerequisites("AWS region audit"));
    } finally {
        await fs.rm(tempRoot, {recursive: true, force: true});
    }
});
