package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record AwsRegionAuditToolRequest(
        @Size(max = 20) List<String> regions,
        @Size(max = 14) List<String> services
) {
}
