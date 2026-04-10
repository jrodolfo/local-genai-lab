package net.jrodolfo.llm.controller;

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

@RestController
@Validated
@RequestMapping("/api/tools")
public class McpToolController {

    private final McpService mcpService;

    public McpToolController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @GetMapping
    public McpToolListResponse listTools() {
        return mcpService.listTools();
    }

    @PostMapping("/aws-region-audit")
    public McpToolInvocationResponse runAwsRegionAudit(@Valid @RequestBody AwsRegionAuditToolRequest request) {
        return mcpService.runAwsRegionAudit(request);
    }

    @PostMapping("/s3-cloudwatch-report")
    public McpToolInvocationResponse runS3CloudwatchReport(@Valid @RequestBody S3CloudwatchReportToolRequest request) {
        return mcpService.runS3CloudwatchReport(request);
    }

    @GetMapping("/reports")
    public McpToolInvocationResponse listRecentReports(
            @RequestParam(required = false)
            @Pattern(regexp = "audit|s3_cloudwatch|all", message = "reportType must be audit, s3_cloudwatch, or all")
            String reportType,
            @RequestParam(required = false) @Min(1) @Max(20) Integer limit
    ) {
        return mcpService.listRecentReports(new ListReportsRequest(reportType, limit));
    }

    @PostMapping("/reports/read")
    public McpToolInvocationResponse readReportSummary(@Valid @RequestBody ReadReportSummaryToolRequest request) {
        return mcpService.readReportSummary(request);
    }

    @ExceptionHandler(McpClientException.class)
    public ResponseEntity<Map<String, String>> handleMcpError(McpClientException ex) {
        HttpStatus status = "MCP integration is disabled.".equals(ex.getMessage())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;

        return ResponseEntity.status(status)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }
}
