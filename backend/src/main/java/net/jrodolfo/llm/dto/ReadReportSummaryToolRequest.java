package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for the tool that reads existing report summaries.
 *
 * @param runDir       backend-visible report run directory under the reports root
 * @param previewLines number of report.txt lines to include in the preview
 */
public record ReadReportSummaryToolRequest(
        @NotBlank String runDir,
        @Min(1) @Max(80) Integer previewLines
) {
}
