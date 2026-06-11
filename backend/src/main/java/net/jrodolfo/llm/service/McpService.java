package net.jrodolfo.llm.service;

import net.jrodolfo.llm.client.McpClient;
import net.jrodolfo.llm.config.McpProperties;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.McpToolInvocationResponse;
import net.jrodolfo.llm.dto.McpToolListResponse;
import net.jrodolfo.llm.dto.ReadReportSummaryToolRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-facing wrapper around the local MCP client.
 *
 * <p>This service is the backend boundary for MCP integration. Controllers and
 * orchestration code call typed Java methods, while this class translates those
 * calls into stable MCP tool names and snake_case argument payloads.
 */
@Service
public class McpService {

    private final McpClient mcpClient;
    private final McpProperties mcpProperties;

    /**
     * Constructs a new McpService.
     *
     * @param mcpClient     the MCP client
     * @param mcpProperties properties for MCP configuration
     */
    public McpService(McpClient mcpClient, McpProperties mcpProperties) {
        this.mcpClient = mcpClient;
        this.mcpProperties = mcpProperties;
    }

    /**
     * Lists available tools from the MCP client.
     *
     * @return a response containing the list of tools
     */
    public McpToolListResponse listTools() {
        if (!mcpProperties.enabled()) {
            return new McpToolListResponse(false, List.of());
        }

        List<McpToolListResponse.McpToolResponse> tools = mcpClient.listTools().stream()
                .map(tool -> new McpToolListResponse.McpToolResponse(
                        tool.name(),
                        tool.title(),
                        tool.description(),
                        tool.inputSchema()
                ))
                .toList();

        return new McpToolListResponse(true, tools);
    }

    /**
     * Runs the AWS region audit MCP tool.
     *
     * @param request typed audit request from the REST or orchestration layer
     * @return tool invocation response with the raw structured MCP result
     */
    public McpToolInvocationResponse runAwsRegionAudit(AwsRegionAuditToolRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (request.regions() != null && !request.regions().isEmpty()) {
            arguments.put("regions", request.regions());
        }
        if (request.services() != null && !request.services().isEmpty()) {
            arguments.put("services", request.services());
        }
        return new McpToolInvocationResponse("aws_region_audit", mcpClient.callTool("aws_region_audit", arguments));
    }

    /**
     * Runs the one-bucket S3 CloudWatch report MCP tool.
     *
     * @param request typed report request; bucket is required and trimmed before invocation
     * @return tool invocation response with the raw structured MCP result
     */
    public McpToolInvocationResponse runS3CloudwatchReport(S3CloudwatchReportToolRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("bucket", request.bucket().trim());
        if (request.region() != null && !request.region().isBlank()) {
            arguments.put("region", request.region().trim());
        }
        if (request.days() != null) {
            arguments.put("days", request.days());
        }
        return new McpToolInvocationResponse("s3_cloudwatch_report", mcpClient.callTool("s3_cloudwatch_report", arguments));
    }

    /**
     * Lists recent generated report directories through the MCP tool.
     *
     * @param request report type and result limit
     * @return tool invocation response with recent report metadata
     */
    public McpToolInvocationResponse listRecentReports(ListReportsRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("report_type", normalizeReportType(request.reportType()));
        arguments.put("limit", request.limit() != null ? request.limit() : 10);
        return new McpToolInvocationResponse("list_recent_reports", mcpClient.callTool("list_recent_reports", arguments));
    }

    /**
     * Reads a summary and preview for an existing generated report.
     *
     * @param request report run directory and preview line count
     * @return tool invocation response with parsed report summary and preview
     */
    public McpToolInvocationResponse readReportSummary(ReadReportSummaryToolRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("run_dir", request.runDir().trim());
        arguments.put("preview_lines", request.previewLines() != null ? request.previewLines() : 20);
        return new McpToolInvocationResponse("read_report_summary", mcpClient.callTool("read_report_summary", arguments));
    }

    /**
     * Normalizes the report type string.
     *
     * @param reportType the raw report type
     * @return the normalized report type
     */
    private String normalizeReportType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            return "all";
        }

        String normalized = reportType.trim();
        return switch (normalized) {
            case "audit", "s3_cloudwatch", "all" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported reportType: " + reportType);
        };
    }
}
