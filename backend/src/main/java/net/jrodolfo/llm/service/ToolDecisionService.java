package net.jrodolfo.llm.service;

import net.jrodolfo.llm.config.AppToolsProperties;
import net.jrodolfo.llm.model.PendingToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chooses whether a user message should trigger an MCP-backed tool.
 *
 * <p>The routing strategy can be rule-only, planner-only, or hybrid. Hybrid mode uses the LLM
 * planner when helpful but preserves deterministic rule-based behavior for a few bounded cases.
 */
@Service
public class ToolDecisionService {

    private static final Logger log = LoggerFactory.getLogger(ToolDecisionService.class);
    private static final Pattern REGION_PATTERN = Pattern.compile("\\b(af|ap|ca|eu|il|me|sa|us)-[a-z]+-\\d\\b");
    private static final List<String> TOOL_SIGNAL_TERMS = List.of(
            "audit", "report", "reports", "bucket", "cloudwatch", "s3", "latest report",
            "recent reports", "read report", "list reports", "show report",
            "aws-config", "aws config", "sts", "ec2", "elbv2", "rds", "lambda",
            "ecs", "eks", "sagemaker", "opensearch", "secretsmanager", "secrets manager",
            "log groups", "tagging"
    );

    private final AppToolsProperties appToolsProperties;
    private final LlmToolPlannerService llmToolPlannerService;
    private final ChatToolRouterService ruleBasedRouter;

    /**
     * Constructs a new ToolDecisionService.
     *
     * @param appToolsProperties properties for application tools
     * @param llmToolPlannerService the service for LLM-based tool planning
     * @param ruleBasedRouter the service for rule-based tool routing
     */
    public ToolDecisionService(
            AppToolsProperties appToolsProperties,
            LlmToolPlannerService llmToolPlannerService,
            ChatToolRouterService ruleBasedRouter
    ) {
        this.appToolsProperties = appToolsProperties;
        this.llmToolPlannerService = llmToolPlannerService;
        this.ruleBasedRouter = ruleBasedRouter;
    }

    /**
     * Routes a user message to a tool decision.
     *
     * @param message the user message
     * @param provider the model provider
     * @param model the model name
     * @return the tool decision
     */
    public ChatToolRouterService.ToolDecision route(String message, String provider, String model) {
        return routeDetailed(message, provider, model).finalDecision();
    }

    /**
     * Resolves a pending tool call based on a follow-up message.
     *
     * @param pendingToolCall the pending tool call
     * @param message the follow-up message
     * @param provider the model provider
     * @param model the model name
     * @return the resolved tool decision
     */
    public ChatToolRouterService.ToolDecision resolvePending(PendingToolCall pendingToolCall, String message, String provider, String model) {
        return resolvePendingDetailed(pendingToolCall, message, provider, model).finalDecision();
    }

    /**
     * Routes a user message to a tool decision and returns a detailed trace.
     *
     * @param message the user message
     * @param provider the model provider
     * @param model the model name
     * @return the decision trace
     */
    public DecisionTrace routeDetailed(String message, String provider, String model) {
        DecisionTrace trace = switch (routingMode()) {
            case "rules" -> new DecisionTrace(
                    "rules",
                    false,
                    false,
                    null,
                    null,
                    ruleBasedRouter.route(message)
            );
            case "llm" -> {
                LlmToolPlannerService.PlanningResult planningResult = llmToolPlannerService.planDetailed(message, provider, model);
                yield new DecisionTrace(
                        "llm",
                        true,
                        false,
                        planningResult.rawResponse(),
                        planningResult.parsedDecision().orElse(null),
                        planningResult.parsedDecision().orElse(ChatToolRouterService.ToolDecision.none())
                );
            }
            default -> {
                // Plain conversational turns should not pay an LLM planning round-trip unless the
                // message already looks tool-related.
                if (shouldSkipPlannerForPlainChat(message)) {
                    yield new DecisionTrace(
                            "hybrid",
                            false,
                            false,
                            null,
                            null,
                            ruleBasedRouter.route(message)
                    );
                }
                LlmToolPlannerService.PlanningResult planningResult = llmToolPlannerService.planDetailed(message, provider, model);
                ChatToolRouterService.ToolDecision rulesDecision = ruleBasedRouter.route(message);
                ChatToolRouterService.ToolDecision finalDecision;
                boolean fallbackUsed = planningResult.parsedDecision().isEmpty();

                if (rulesDecision.shouldUseTool()) {
                    // When the rules engine can satisfy the request deterministically, prefer that
                    // over a planner clarification that leaks tool-internal wording or asks for
                    // inputs that are not actually required.
                    finalDecision = planningResult.parsedDecision()
                            .filter(ChatToolRouterService.ToolDecision::shouldUseTool)
                            .orElse(rulesDecision);
                    fallbackUsed = planningResult.parsedDecision().isEmpty()
                            || !planningResult.parsedDecision().orElse(ChatToolRouterService.ToolDecision.none()).shouldUseTool();
                } else {
                    finalDecision = planningResult.parsedDecision().orElse(rulesDecision);
                }
                yield new DecisionTrace(
                        "hybrid",
                        true,
                        fallbackUsed,
                        planningResult.rawResponse(),
                        planningResult.parsedDecision().orElse(null),
                        finalDecision
                );
            }
        };
        logTrace("route", trace, message);
        return trace;
    }

    /**
     * Resolves a pending tool call and returns a detailed trace.
     *
     * @param pendingToolCall the pending tool call
     * @param message the follow-up message
     * @param provider the model provider
     * @param model the model name
     * @return the decision trace
     */
    public DecisionTrace resolvePendingDetailed(PendingToolCall pendingToolCall, String message, String provider, String model) {
        DecisionTrace trace = switch (routingMode()) {
            case "rules" -> new DecisionTrace(
                    "rules",
                    false,
                    false,
                    null,
                    null,
                    ruleBasedRouter.resolvePending(pendingToolCall, message)
            );
            case "llm" -> {
                LlmToolPlannerService.PlanningResult planningResult =
                        llmToolPlannerService.resolvePendingDetailed(pendingToolCall, message, provider, model);
                yield new DecisionTrace(
                        "llm",
                        true,
                        false,
                        planningResult.rawResponse(),
                        planningResult.parsedDecision().orElse(null),
                        planningResult.parsedDecision().orElse(ChatToolRouterService.ToolDecision.none())
                );
            }
            default -> {
                LlmToolPlannerService.PlanningResult planningResult =
                        llmToolPlannerService.resolvePendingDetailed(pendingToolCall, message, provider, model);
                ChatToolRouterService.ToolDecision rulesDecision = ruleBasedRouter.resolvePending(pendingToolCall, message);
                ChatToolRouterService.ToolDecision finalDecision;
                boolean fallbackUsed = planningResult.parsedDecision().isEmpty();

                if (rulesDecision.shouldUseTool()) {
                    // When a pending clarification can be satisfied deterministically, prefer that
                    // over a planner response that keeps asking for already-supplied input.
                    finalDecision = rulesDecision;
                    fallbackUsed = planningResult.parsedDecision().isEmpty()
                            || !planningResult.parsedDecision().orElse(ChatToolRouterService.ToolDecision.none()).shouldUseTool();
                } else {
                    finalDecision = planningResult.parsedDecision().orElse(rulesDecision);
                }
                yield new DecisionTrace(
                        "hybrid",
                        true,
                        fallbackUsed,
                        planningResult.rawResponse(),
                        planningResult.parsedDecision().orElse(null),
                        finalDecision
                );
            }
        };
        logTrace("resolve_pending", trace, message);
        return trace;
    }

    /**
     * Determines the current routing mode from configuration.
     *
     * @return the routing mode ("rules", "llm", or "hybrid")
     */
    private String routingMode() {
        String configured = appToolsProperties.routingMode();
        if (configured == null || configured.isBlank()) {
            return "hybrid";
        }
        return configured.trim().toLowerCase();
    }

    /**
     * Checks if the LLM planner should be skipped for plain conversational turns.
     *
     * @param message the user message
     * @return true if the planner should be skipped, false otherwise
     */
    private boolean shouldSkipPlannerForPlainChat(String message) {
        if (message == null || message.isBlank()) {
            return true;
        }

        String normalized = message.toLowerCase(Locale.ROOT).trim();
        if (REGION_PATTERN.matcher(normalized).find()) {
            return false;
        }

        return TOOL_SIGNAL_TERMS.stream().noneMatch(normalized::contains);
    }

    /**
     * Logs the decision trace for troubleshooting.
     *
     * @param phase the decision phase ("route" or "resolve_pending")
     * @param trace the decision trace
     * @param message the user message
     */
    private void logTrace(String phase, DecisionTrace trace, String message) {
        if (!appToolsProperties.logPlanner()) {
            return;
        }

        log.info(
                "tool_decision phase={} routingMode={} plannerAttempted={} fallbackUsed={} finalType={} rawPlannerOutput={} parsedDecision={} message={}",
                phase,
                trace.routingMode(),
                trace.plannerAttempted(),
                trace.fallbackUsed(),
                trace.finalDecision().type(),
                trace.rawPlannerOutput(),
                trace.parsedPlannerDecision(),
                message
        );
    }

    /**
     * Record representing a detailed trace of a tool decision.
     *
     * @param routingMode the routing mode used
     * @param plannerAttempted whether the LLM planner was attempted
     * @param fallbackUsed whether a fallback decision was used
     * @param rawPlannerOutput the raw output from the LLM planner
     * @param parsedPlannerDecision the decision parsed from the planner output
     * @param finalDecision the final tool decision
     */
    public record DecisionTrace(
            String routingMode,
            boolean plannerAttempted,
            boolean fallbackUsed,
            String rawPlannerOutput,
            ChatToolRouterService.ToolDecision parsedPlannerDecision,
            ChatToolRouterService.ToolDecision finalDecision
    ) {
    }
}
