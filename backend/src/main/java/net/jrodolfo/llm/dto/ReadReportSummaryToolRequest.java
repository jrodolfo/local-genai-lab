package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for the tool that reads report summaries.
 */
public record ReadReportSummaryToolRequest(
        @NotBlank String runDir,
        @Min(1) @Max(80) Integer previewLines
) {
}
