import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import {
  AUDIT_SERVICES,
  REPORT_TYPES,
  awsRegionAuditSchema,
  listRecentReportsSchema,
  readReportSummarySchema,
  s3CloudwatchReportSchema,
} from "./schemas/toolSchemas.js";
import { handleAwsRegionAudit } from "./tools/awsRegionAudit.js";
import { handleListRecentReports } from "./tools/listReports.js";
import { handleReadReportSummary } from "./tools/readReportSummary.js";
import { handleS3CloudwatchReport } from "./tools/s3CloudwatchReport.js";

function formatToolResult<T extends Record<string, unknown>>(payload: T) {
  return {
    content: [
      {
        type: "text" as const,
        text: JSON.stringify(payload, null, 2),
      },
    ],
    structuredContent: payload,
  };
}

function formatToolError(error: unknown) {
  const message = error instanceof Error ? error.message : "Unknown MCP tool error";

  return {
    isError: true,
    structuredContent: {
      ok: false,
      error: message,
    },
    content: [
      {
        type: "text" as const,
        text: message,
      },
    ],
  };
}

const server = new McpServer({
  name: "llm-pet-project-mcp",
  version: "0.1.0",
});

server.registerTool(
  "aws_region_audit",
  {
    title: "AWS Region Audit",
    description: "Run the repository's AWS regional audit shell script with validated regions and service filters.",
    inputSchema: {
      regions: z.array(z.string().trim().min(1)).max(20).optional(),
      services: z.array(z.enum(AUDIT_SERVICES)).max(AUDIT_SERVICES.length).optional(),
    },
  },
  async (arguments_) => {
    try {
      const parsedInput = awsRegionAuditSchema.parse(arguments_);
      return formatToolResult(await handleAwsRegionAudit(parsedInput));
    } catch (error) {
      return formatToolError(error);
    }
  },
);

server.registerTool(
  "s3_cloudwatch_report",
  {
    title: "S3 CloudWatch Report",
    description: "Run the repository's S3 CloudWatch report shell script for a specific bucket.",
    inputSchema: {
      bucket: z.string().trim().min(3),
      region: z.string().trim().min(1).optional(),
      days: z.number().int().positive().max(365).optional(),
    },
  },
  async (arguments_) => {
    try {
      const parsedInput = s3CloudwatchReportSchema.parse(arguments_);
      return formatToolResult(await handleS3CloudwatchReport(parsedInput));
    } catch (error) {
      return formatToolError(error);
    }
  },
);

server.registerTool(
  "list_recent_reports",
  {
    title: "List Recent Reports",
    description: "List recent audit and S3 CloudWatch report directories that already exist under scripts/reports.",
    inputSchema: {
      report_type: z.enum(REPORT_TYPES).default("all"),
      limit: z.number().int().positive().max(20).default(10),
    },
  },
  async (arguments_) => {
    try {
      const parsedInput = listRecentReportsSchema.parse(arguments_);
      return formatToolResult(await handleListRecentReports(parsedInput));
    } catch (error) {
      return formatToolError(error);
    }
  },
);

server.registerTool(
  "read_report_summary",
  {
    title: "Read Report Summary",
    description: "Read summary.json and a short report.txt preview for an existing report directory under scripts/reports.",
    inputSchema: {
      run_dir: z.string().trim().min(1),
      preview_lines: z.number().int().positive().max(80).default(20),
    },
  },
  async (arguments_) => {
    try {
      const parsedInput = readReportSummarySchema.parse(arguments_);
      return formatToolResult(await handleReadReportSummary(parsedInput));
    } catch (error) {
      return formatToolError(error);
    }
  },
);

const transport = new StdioServerTransport();
await server.connect(transport);
