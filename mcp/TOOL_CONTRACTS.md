# MCP Tool Contracts

This file defines the canonical MCP tool contracts for the local server in this repository.

The source of truth is the runtime Zod schemas under [src/schemas](./src/schemas). This document
summarizes those contracts in a human-readable form.

## Contract Rules

- Input validation happens before a tool handler runs.
- Output validation happens before the MCP server returns a successful structured payload.
- MCP-level failures use one shared error contract.
- A tool result may still be valid when `ok=false`.
  This means the script ran and returned a structured result, but the underlying audit/report
  indicates partial or complete failure.

## Shared Error Contract

All tool failures returned by the MCP layer use this shape:

```json
{
  "ok": false,
  "tool": "aws_region_audit",
  "error": "Human-readable error message"
}
```

Optional future-compatible field:

- `details`: arbitrary JSON details

## `aws_region_audit`

Purpose:

- Run the repository AWS regional audit script and return the produced report bundle.

Input:

```json
{
  "regions": ["us-east-1", "us-east-2"],
  "services": ["ec2", "lambda"]
}
```

Output:

```json
{
  "ok": true,
  "tool": "aws_region_audit",
  "report_type": "audit",
  "run_dir": "/abs/path/to/scripts/reports/audit/aws-audit-...",
  "execution": {
    "command": "./aws-region-audit-report.sh",
    "args": ["--regions", "us-east-1", "us-east-2"],
    "exitCode": 0,
    "signal": null,
    "stdout": "...",
    "stderr": "",
    "durationMs": 1532,
    "timedOut": false
  },
  "summary": {
    "success_count": 37,
    "failure_count": 0,
    "skipped_count": 0
  },
  "report_preview": "First lines of report.txt"
}
```

Invariants:

- `report_type` is always `audit`
- `run_dir` points to the created audit run directory
- `summary` is arbitrary JSON from `summary.json`

## `s3_cloudwatch_report`

Purpose:

- Run the repository S3 CloudWatch script for one bucket and return the produced report bundle.

Input:

```json
{
  "bucket": "example.com",
  "region": "us-east-2",
  "days": 14
}
```

Output:

```json
{
  "ok": true,
  "tool": "s3_cloudwatch_report",
  "report_type": "s3_cloudwatch",
  "run_dir": "/abs/path/to/scripts/reports/s3-cloudwatch/s3-cloudwatch-...",
  "execution": {
    "command": "./aws-s3-cloudwatch-report.sh",
    "args": ["--bucket", "example.com"],
    "exitCode": 0,
    "signal": null,
    "stdout": "...",
    "stderr": "",
    "durationMs": 918,
    "timedOut": false
  },
  "summary": {
    "bucket": "example.com",
    "success_count": 2,
    "failure_count": 0
  },
  "report_preview": "First lines of report.txt"
}
```

Invariants:

- `report_type` is always `s3_cloudwatch`
- `summary` is arbitrary JSON from `summary.json`

## `list_recent_reports`

Purpose:

- List the most recent report directories already present under `scripts/reports`.

Input:

```json
{
  "report_type": "all",
  "limit": 10
}
```

Output:

```json
{
  "ok": true,
  "tool": "list_recent_reports",
  "report_type": "all",
  "reports": [
    {
      "report_type": "audit",
      "run_dir": "/abs/path/to/scripts/reports/audit/aws-audit-...",
      "directory_name": "aws-audit-...",
      "created_at": "2026-04-17T10:00:00.000Z",
      "report_txt": "/abs/path/to/scripts/reports/audit/aws-audit-.../report.txt",
      "summary_json": "/abs/path/to/scripts/reports/audit/aws-audit-.../summary.json"
    }
  ]
}
```

Invariants:

- `report_type` is one of `audit`, `s3_cloudwatch`, or `all`
- each report entry points to an existing run directory under `scripts/reports`

## `read_report_summary`

Purpose:

- Read `summary.json` plus a short `report.txt` preview for an existing report directory.

Input:

```json
{
  "run_dir": "/abs/path/to/scripts/reports/audit/aws-audit-...",
  "preview_lines": 20
}
```

Output:

```json
{
  "ok": true,
  "tool": "read_report_summary",
  "report_type": "audit",
  "run_dir": "/abs/path/to/scripts/reports/audit/aws-audit-...",
  "summary": {
    "success_count": 37,
    "failure_count": 0
  },
  "report_preview": "First lines of report.txt"
}
```

Invariants:

- `run_dir` must resolve under `scripts/reports`
- `report_type` is inferred from the run directory
- `summary` is arbitrary JSON from `summary.json`
