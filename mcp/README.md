# MCP Server

[![ci](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml)
![license](https://img.shields.io/badge/license-MIT-blue)
![node](https://img.shields.io/badge/node-20+-5fa04e)
![typescript](https://img.shields.io/badge/typescript-mcp%20server-3178c6)
![mcp](https://img.shields.io/badge/mcp-stdio-0a7ea4)
![aws](https://img.shields.io/badge/aws-local%20tools-a1662f)

Local Model Context Protocol server for the `local-genai-lab` repository.

It wraps the sibling shell tools in [`../scripts`](../scripts) and exposes a small allowlist of explicit tools instead of arbitrary shell access.

## Tools

- `aws_region_audit`
- `s3_cloudwatch_report`
- `list_recent_reports`
- `read_report_summary`

Formal input/output contracts for these tools are documented in [TOOL_CONTRACTS.md](./TOOL_CONTRACTS.md).

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

The Spring backend can also launch this server automatically. In the default local setup, MCP is enabled in the backend by default.

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

## Backend Defaults

When launched by the backend, the default MCP settings are:

- `MCP_ENABLED=true`
- `MCP_COMMAND=node`
- `MCP_WORKING_DIRECTORY=mcp`
- `MCP_ARG_1=dist/index.js`
