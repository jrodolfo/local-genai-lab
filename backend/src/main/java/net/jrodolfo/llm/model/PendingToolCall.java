package net.jrodolfo.llm.model;

import net.jrodolfo.llm.service.ChatToolRouterService;

import java.util.List;

/**
 * Represents a tool call that is pending user confirmation or more information.
 *
 * @param type          the type of decision required (e.g., confirm, clarify)
 * @param reportType    the type of report being generated
 * @param bucket        the S3 bucket involved
 * @param region        the AWS region involved
 * @param days          the number of days for the report range
 * @param reason        the reason for the tool call or why it's pending
 * @param services      the list of services involved
 * @param missingFields fields that need to be provided to complete the tool call
 */
public record PendingToolCall(
        ChatToolRouterService.DecisionType type,
        String reportType,
        String bucket,
        String region,
        Integer days,
        String reason,
        List<String> services,
        List<String> missingFields
) {
    /**
     * Compact constructor to ensure lists are not null and are immutable.
     */
    public PendingToolCall {
        services = services == null ? List.of() : List.copyOf(services);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }
}
