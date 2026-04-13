package net.jrodolfo.llm.service;

import net.jrodolfo.llm.config.AppToolsProperties;
import net.jrodolfo.llm.model.PendingToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ToolDecisionService {

    private static final Logger log = LoggerFactory.getLogger(ToolDecisionService.class);

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

    public ChatToolRouterService.ToolDecision route(String message, String model) {
        return routeDetailed(message, model).finalDecision();
    }

    public ChatToolRouterService.ToolDecision resolvePending(PendingToolCall pendingToolCall, String message, String model) {
        return resolvePendingDetailed(pendingToolCall, message, model).finalDecision();
    }

    public DecisionTrace routeDetailed(String message, String model) {
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
                LlmToolPlannerService.PlanningResult planningResult = llmToolPlannerService.planDetailed(message, model);
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
                LlmToolPlannerService.PlanningResult planningResult = llmToolPlannerService.planDetailed(message, model);
                ChatToolRouterService.ToolDecision finalDecision = planningResult.parsedDecision()
                        .orElseGet(() -> ruleBasedRouter.route(message));
                yield new DecisionTrace(
                        "hybrid",
                        true,
                        planningResult.parsedDecision().isEmpty(),
                        planningResult.rawResponse(),
                        planningResult.parsedDecision().orElse(null),
                        finalDecision
                );
            }
        };
        logTrace("route", trace, message);
        return trace;
    }

    public DecisionTrace resolvePendingDetailed(PendingToolCall pendingToolCall, String message, String model) {
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
                        llmToolPlannerService.resolvePendingDetailed(pendingToolCall, message, model);
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
                        llmToolPlannerService.resolvePendingDetailed(pendingToolCall, message, model);
                ChatToolRouterService.ToolDecision rulesDecision = ruleBasedRouter.resolvePending(pendingToolCall, message);
                ChatToolRouterService.ToolDecision finalDecision;
                boolean fallbackUsed = planningResult.parsedDecision().isEmpty();

                if (rulesDecision.shouldUseTool()) {
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
