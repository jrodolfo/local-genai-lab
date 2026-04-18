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

    public ToolDecisionService(
            AppToolsProperties appToolsProperties,
            LlmToolPlannerService llmToolPlannerService,
            ChatToolRouterService ruleBasedRouter
    ) {
        this.appToolsProperties = appToolsProperties;
        this.llmToolPlannerService = llmToolPlannerService;
        this.ruleBasedRouter = ruleBasedRouter;
    }

    public ChatToolRouterService.ToolDecision route(String message, String provider, String model) {
        return routeDetailed(message, provider, model).finalDecision();
    }

    public ChatToolRouterService.ToolDecision resolvePending(PendingToolCall pendingToolCall, String message, String provider, String model) {
        return resolvePendingDetailed(pendingToolCall, message, provider, model).finalDecision();
    }

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

    private String routingMode() {
        String configured = appToolsProperties.routingMode();
        if (configured == null || configured.isBlank()) {
            return "hybrid";
        }
        return configured.trim().toLowerCase();
    }

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
