package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Pending tool clarification state exposed to the frontend.
 *
 * <p>This payload is stored on sessions when the backend recognized a tool
 * intent but needs more information, for example an S3 bucket name.
 *
 * @param toolName      MCP tool name that is waiting for input
 * @param reason        routing reason or clarification context
 * @param missingFields fields the next user message should provide
 * @param reportType    report type for report-reading flows, when applicable
 * @param bucket        S3 bucket for pending S3 report flows, when known
 * @param region        AWS region for pending AWS flows, when known
 * @param days          requested lookback window for pending S3 report flows, when known
 * @param services      AWS service filters for pending audit flows, when known
 * @param bucketOptions known S3 buckets that can complete this pending call
 */
@Schema(description = "Pending tool clarification state exposed to the frontend.")
public record PendingToolCallResponse(
        String toolName,
        String reason,
        List<String> missingFields,
        String reportType,
        String bucket,
        String region,
        Integer days,
        List<String> services,
        List<String> bucketOptions
) {
}
