package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for the S3 CloudWatch Report tool.
 */
public record S3CloudwatchReportToolRequest(
        @NotBlank String bucket,
        String region,
        @Min(1) @Max(365) Integer days
) {
}
