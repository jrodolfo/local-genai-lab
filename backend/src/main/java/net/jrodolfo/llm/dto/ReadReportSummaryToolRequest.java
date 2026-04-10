package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReadReportSummaryToolRequest(
        @NotBlank String runDir,
        @Min(1) @Max(80) Integer previewLines
) {
}
