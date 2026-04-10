# MCP Server

Local Model Context Protocol server for the `llm-pet-project` repository.

It wraps the sibling shell tools in [`../scripts`](../scripts) and exposes a small allowlist of explicit tools instead of arbitrary shell access.

## Tools

- `aws_region_audit`
- `s3_cloudwatch_report`
- `list_recent_reports`
- `read_report_summary`

## Requirements

- Node 20+
- AWS CLI v2
- `jq`
- valid AWS credentials

The server also depends on the existing sibling [`../scripts`](../scripts) directory.

## Install

```bash
cd mcp
npm install
```

## Run

Development:

```bash
cd mcp
npm run dev
```

Build:

```bash
cd mcp
npm run build
```

Start compiled server:

```bash
cd mcp
npm start
```

## Notes

- Transport is local `stdio`.
- The server reads and writes only under the repository's `scripts/` subtree.
- Report-reading tools are restricted to [`../scripts/reports`](../scripts/reports).
- Script execution is restricted to the documented entrypoints:
  - [`../scripts/aws-region-audit-report.sh`](../scripts/aws-region-audit-report.sh)
  - [`../scripts/aws-s3-cloudwatch-report.sh`](../scripts/aws-s3-cloudwatch-report.sh)

## Environment

- `MCP_AUDIT_TIMEOUT_MS`
- `MCP_S3_REPORT_TIMEOUT_MS`
