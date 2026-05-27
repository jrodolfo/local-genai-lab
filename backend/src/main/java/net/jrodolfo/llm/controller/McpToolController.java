package net.jrodolfo.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.McpToolInvocationResponse;
import net.jrodolfo.llm.dto.McpToolListResponse;
import net.jrodolfo.llm.dto.ReadReportSummaryToolRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import net.jrodolfo.llm.service.McpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for interacting with Model Context Protocol (MCP) tools.
 */
@RestController
@Validated
@RequestMapping("/api/tools")
@Tag(name = "tools", description = "Optional local MCP-backed AWS audit and report tooling.")
public class McpToolController {

    private final McpService mcpService;

    /**
     * Constructs a new McpToolController with the specified McpService.
     *
     * @param mcpService the service for MCP tool operations.
     */
    public McpToolController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    /**
     * Lists all available MCP tools.
     *
     * @return a list of available tools.
     */
    @GetMapping
    @Operation(summary = "List available MCP tools", description = "Returns the locally configured MCP tools exposed through the backend.")
    public McpToolListResponse listTools() {
        return mcpService.listTools();
    }

    /**
     * Runs the AWS regional audit tool.
     *
     * @param request the tool invocation request.
     * @return the result of the tool invocation.
     */
    @PostMapping("/aws-region-audit")
    @Operation(summary = "Run the AWS regional audit tool", description = "Invokes the local MCP-backed AWS regional audit shell workflow.")
    public McpToolInvocationResponse runAwsRegionAudit(@Valid @RequestBody AwsRegionAuditToolRequest request) {
        return mcpService.runAwsRegionAudit(request);
    }

    /**
     * Runs the S3 CloudWatch report tool.
     *
     * @param request the tool invocation request.
     * @return the result of the tool invocation.
     */
    @PostMapping("/s3-cloudwatch-report")
    @Operation(summary = "Run the S3 CloudWatch report tool", description = "Invokes the local MCP-backed S3 CloudWatch shell workflow.")
    public McpToolInvocationResponse runS3CloudwatchReport(@Valid @RequestBody S3CloudwatchReportToolRequest request) {
        return mcpService.runS3CloudwatchReport(request);
    }

    /**
     * Lists recent report directories.
     *
     * @param reportType optional filter for report type.
     * @param limit      optional limit on the number of reports returned.
     * @return the tool invocation response containing the list of reports.
     */
    @GetMapping("/reports")
    @Operation(summary = "List recent report directories", description = "Lists recent audit or S3 CloudWatch report directories under the local reports tree.")
    public McpToolInvocationResponse listRecentReports(
            @Parameter(description = "Optional report type filter: `audit`, `s3_cloudwatch`, or `all`.", example = "all")
            @RequestParam(required = false)
            @Pattern(regexp = "audit|s3_cloudwatch|all", message = "reportType must be audit, s3_cloudwatch, or all")
            String reportType,
            @Parameter(description = "Maximum number of reports to return, between 1 and 20.", example = "5")
            @RequestParam(required = false) @Min(1) @Max(20) Integer limit
    ) {
        return mcpService.listRecentReports(new ListReportsRequest(reportType, limit));
    }

    /**
     * Reads a report summary bundle.
     *
     * @param request the request containing the report path.
     * @return the report summary and preview.
     */
    @PostMapping("/reports/read")
    @Operation(summary = "Read a report summary bundle", description = "Reads a report bundle under the local reports tree and returns summary data plus a short text preview.")
    public McpToolInvocationResponse readReportSummary(@Valid @RequestBody ReadReportSummaryToolRequest request) {
        return mcpService.readReportSummary(request);
    }

    /**
     * Exception handler for McpClientException.
     *
     * @param ex the exception.
     * @return a ResponseEntity with error details.
     */
    @ExceptionHandler(McpClientException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleMcpError(McpClientException ex) {
        HttpStatus status = "MCP integration is disabled.".equals(ex.getMessage())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;

        return ResponseEntity.status(status)
                .body(Map.of("error", ex.getMessage()));
    }

    /**
     * Exception handler for IllegalArgumentException.
     *
     * @param ex the exception.
     * @return a ResponseEntity with error details.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleInvalidInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }
}
