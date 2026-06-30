import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test, {afterEach} from "node:test";
import {config} from "../config.js";
import {listRecentReportsResultSchema, readReportSummaryResultSchema} from "../schemas/toolContracts.js";
import {handleListRecentReports} from "./listReports.js";
import {handleReadReportSummary} from "./readReportSummary.js";

const fixturesRoot = path.resolve("src/test/fixtures/reports");
const originalConfig = {
    reportsDir: config.reportsDir,
    auditReportsDir: config.auditReportsDir,
    s3ReportsDir: config.s3ReportsDir,
};

async function setupFixtureReports() {
    const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "local-genai-lab-mcp-"));
    const reportsRoot = path.join(tempRoot, "reports");
    await fs.cp(fixturesRoot, reportsRoot, {recursive: true});

    Object.assign(config as { reportsDir: string; auditReportsDir: string; s3ReportsDir: string }, {
        reportsDir: reportsRoot,
        auditReportsDir: path.join(reportsRoot, "audit"),
        s3ReportsDir: path.join(reportsRoot, "s3-cloudwatch"),
    });

    return tempRoot;
}

async function touchRelative(targetRoot: string, relativePath: string, mtime: Date) {
    const absolutePath = path.join(targetRoot, relativePath);
    await fs.utimes(absolutePath, mtime, mtime);
}

afterEach(async () => {
    Object.assign(config as { reportsDir: string; auditReportsDir: string; s3ReportsDir: string }, originalConfig);
});

test("list recent reports returns newest valid runs first and ignores incomplete directories", async () => {
    const tempRoot = await setupFixtureReports();

    try {
        await touchRelative(tempRoot, "reports/audit/aws-audit-valid", new Date("2026-04-18T12:00:00.000Z"));
        await touchRelative(tempRoot, "reports/audit/aws-audit-malformed-summary", new Date("2026-04-18T11:00:00.000Z"));
        await touchRelative(tempRoot, "reports/s3-cloudwatch/s3-cloudwatch-valid", new Date("2026-04-18T13:00:00.000Z"));

        const result = await handleListRecentReports({report_type: "all", limit: 10});
        const parsed = listRecentReportsResultSchema.parse(result);

        assert.deepEqual(
            parsed.reports.map((report) => report.directory_name),
            ["s3-cloudwatch-valid", "aws-audit-valid", "aws-audit-malformed-summary"],
        );
        assert.equal(parsed.reports.some((report) => report.directory_name === "aws-audit-missing-summary"), false);
    } finally {
        await fs.rm(tempRoot, {recursive: true, force: true});
    }
});

test("list recent reports filters by report type", async () => {
    const tempRoot = await setupFixtureReports();

    try {
        const result = await handleListRecentReports({report_type: "audit", limit: 10});
        const parsed = listRecentReportsResultSchema.parse(result);

        assert.equal(parsed.reports.length, 2);
        assert.ok(parsed.reports.every((report) => report.report_type === "audit"));
    } finally {
        await fs.rm(tempRoot, {recursive: true, force: true});
    }
});

test("list recent reports returns an empty list when no report bundles exist", async () => {
    const tempRoot = await fs.mkdtemp(path.join(os.tmpdir(), "local-genai-lab-mcp-empty-"));

    try {
        const reportsRoot = path.join(tempRoot, "reports");
        await fs.mkdir(path.join(reportsRoot, "audit"), {recursive: true});
        await fs.mkdir(path.join(reportsRoot, "s3-cloudwatch"), {recursive: true});

        Object.assign(config as { reportsDir: string; auditReportsDir: string; s3ReportsDir: string }, {
            reportsDir: reportsRoot,
            auditReportsDir: path.join(reportsRoot, "audit"),
            s3ReportsDir: path.join(reportsRoot, "s3-cloudwatch"),
        });

        const result = await handleListRecentReports({report_type: "all", limit: 10});
        const parsed = listRecentReportsResultSchema.parse(result);

        assert.deepEqual(parsed.reports, []);
    } finally {
        await fs.rm(tempRoot, {recursive: true, force: true});
    }
});

test("read report summary returns a structured bundle with truncated preview lines", async () => {
    const tempRoot = await setupFixtureReports();

    try {
        const runDir = path.join(config.auditReportsDir, "aws-audit-valid");
        const result = await handleReadReportSummary({run_dir: runDir, preview_lines: 2});
        const parsed = readReportSummaryResultSchema.parse(result);
        const summary = parsed.summary as Record<string, unknown>;

        assert.equal(parsed.report_type, "audit");
        assert.equal(parsed.report_preview, ["Audit report line 1", "Audit report line 2"].join("\n"));
        assert.equal(summary.account_id, "123456789012");
        assert.equal(
            summary.report_path,
            path.join(config.agentsDir, "reports/audit/aws-audit-valid/report.txt"),
        );
        assert.equal(
            summary.output_directory,
            path.join(config.agentsDir, "reports/audit/aws-audit-valid"),
        );
    } finally {
        await fs.rm(tempRoot, {recursive: true, force: true});
    }
});

test("read report summary rejects paths outside the reports root", async () => {
    const tempRoot = await setupFixtureReports();

    try {
        await assert.rejects(
            () => handleReadReportSummary({run_dir: path.resolve(tempRoot, ".."), preview_lines: 20}),
            /outside the allowed reports directory/,
        );
    } finally {
        await fs.rm(tempRoot, {recursive: true, force: true});
    }
});

test("read report summary fails clearly for malformed summary json", async () => {
    const tempRoot = await setupFixtureReports();

    try {
        const runDir = path.join(config.auditReportsDir, "aws-audit-malformed-summary");

        await assert.rejects(
            () => handleReadReportSummary({run_dir: runDir, preview_lines: 20}),
            /JSON/,
        );
    } finally {
        await fs.rm(tempRoot, {recursive: true, force: true});
    }
});
