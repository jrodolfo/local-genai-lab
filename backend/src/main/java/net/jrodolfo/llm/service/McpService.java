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
 * <p>This service isolates HTTP/controller code from MCP tool names and argument shapes while
 * keeping the repository's MCP usage small and explicit.
 */
@Service
public class McpService {

    private final McpClient mcpClient;
    private final McpProperties mcpProperties;

    /**
     * Constructs a new McpService.
     *
     * @param mcpClient the MCP client
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
     * Runs an AWS region audit tool.
     *
     * @param request the audit request
     * @return the tool invocation response
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
     * Runs an S3 CloudWatch report tool.
     *
     * @param request the report request
     * @return the tool invocation response
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
     * Lists recent reports using the MCP tool.
     *
     * @param request the list reports request
     * @return the tool invocation response
     */
    public McpToolInvocationResponse listRecentReports(ListReportsRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("report_type", normalizeReportType(request.reportType()));
        arguments.put("limit", request.limit() != null ? request.limit() : 10);
        return new McpToolInvocationResponse("list_recent_reports", mcpClient.callTool("list_recent_reports", arguments));
    }

    /**
     * Reads a report summary using the MCP tool.
     *
     * @param request the read report summary request
     * @return the tool invocation response
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
