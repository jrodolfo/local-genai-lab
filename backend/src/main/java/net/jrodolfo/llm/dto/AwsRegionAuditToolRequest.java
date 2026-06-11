package net.jrodolfo.llm.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for the AWS Region Audit tool.
 *
 * @param regions  optional region filter. When omitted, the shell script uses its defaults.
 * @param services optional service filter using the MCP-supported audit service keys.
 */
public record AwsRegionAuditToolRequest(
        @Size(max = 20) List<String> regions,
        @Size(max = 14) List<String> services
) {
}
