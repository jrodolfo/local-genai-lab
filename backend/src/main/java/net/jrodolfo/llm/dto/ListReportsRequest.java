package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request payload for listing generated report bundles.
 *
 * @param reportType report family filter: {@code audit}, {@code s3_cloudwatch}, or {@code all}
 * @param limit      maximum number of report references to return
 */
public record ListReportsRequest(
        String reportType,
        @Min(1) @Max(20) Integer limit
) {
}
