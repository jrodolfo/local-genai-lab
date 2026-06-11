package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for the one-bucket S3 CloudWatch Report tool.
 *
 * @param bucket S3 bucket name to inspect
 * @param region optional request-metrics region; when omitted, the script detects the bucket region
 * @param days   CloudWatch lookback window in days
 */
public record S3CloudwatchReportToolRequest(
        @NotBlank String bucket,
        String region,
        @Min(1) @Max(365) Integer days
) {
}
