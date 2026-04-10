package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ListReportsRequest(
        String reportType,
        @Min(1) @Max(20) Integer limit
) {
}
