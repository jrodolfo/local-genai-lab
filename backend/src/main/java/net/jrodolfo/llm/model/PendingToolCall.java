package net.jrodolfo.llm.model;

import net.jrodolfo.llm.service.ChatToolRouterService;

import java.util.List;

/**
 * Persisted tool clarification state.
 *
 * <p>The router stores this record on a session when it needs a follow-up user
 * message before running a tool. The next turn can complete the missing fields
 * without requiring the user to repeat the original request.
 *
 * @param type          the router decision type waiting for completion
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
