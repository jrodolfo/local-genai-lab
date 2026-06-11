package net.jrodolfo.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tool provenance attached to assistant responses when a local MCP-backed tool was involved.
 *
 * @param used    true when the assistant turn involved a tool decision or execution
 * @param name    MCP tool name, such as {@code aws_region_audit} or {@code s3_cloudwatch_report}
 * @param status  normalized tool status, such as {@code success}, {@code failed}, or {@code clarification-needed}
 * @param summary short user-facing result or clarification text
 */
@Schema(description = "Tool provenance attached to assistant responses when a local MCP-backed tool was involved.")
public record ChatToolMetadata(
        boolean used,
        String name,
        String status,
        String summary
) {
}
