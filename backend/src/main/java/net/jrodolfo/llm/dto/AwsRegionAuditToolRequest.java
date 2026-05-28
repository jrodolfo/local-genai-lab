package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for the AWS Region Audit tool.
 */
public record AwsRegionAuditToolRequest(
        @Size(max = 20) List<String> regions,
        @Size(max = 14) List<String> services
) {
}
