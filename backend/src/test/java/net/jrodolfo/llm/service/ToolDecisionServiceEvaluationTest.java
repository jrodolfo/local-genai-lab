package net.jrodolfo.llm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.AppToolsProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.model.PendingToolCall;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import net.jrodolfo.llm.provider.ProviderPrompt;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDecisionServiceEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluatesFixtureSetAgainstPlannerAndFallbackBehavior() throws Exception {
        List<EvaluationFixture> fixtures;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("tool-decision-evaluation-fixtures.json")) {
            assertNotNull(inputStream);
            fixtures = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }

        List<String> matchedCases = new ArrayList<>();

        for (EvaluationFixture fixture : fixtures) {
            FakeChatModelProvider chatModelProvider = new FakeChatModelProvider(fixture.plannerResponse());
            ChatModelProviderRegistry registry = new ChatModelProviderRegistry(
                    new net.jrodolfo.llm.config.AppModelProperties("ollama"),
                    java.util.Map.of("ollama", chatModelProvider)
            );
            ToolDecisionService service = new ToolDecisionService(
                    new AppToolsProperties(fixture.mode(), false),
                    new LlmToolPlannerService(registry, objectMapper),
                    new ChatToolRouterService()
            );

            ToolDecisionService.DecisionTrace trace = fixture.pendingTool() == null
                    ? service.routeDetailed(fixture.message(), "ollama", "llama3:8b")
                    : service.resolvePendingDetailed(toPendingToolCall(fixture.pendingTool()), fixture.message(), "ollama", "llama3:8b");

            assertEquals(ChatToolRouterService.DecisionType.valueOf(fixture.expectedType()), trace.finalDecision().type(), fixture.name());
            assertEquals(fixture.expectedUseTool(), trace.finalDecision().shouldUseTool(), fixture.name());
            assertEquals(fixture.expectedClarification(), trace.finalDecision().needsClarification(), fixture.name());
            assertEquals(fixture.expectedFallbackUsed(), trace.fallbackUsed(), fixture.name());

            if (fixture.expectedBucket() != null) {
                assertEquals(fixture.expectedBucket(), trace.finalDecision().bucket(), fixture.name());
            }
            if (fixture.expectedRegion() != null) {
                assertEquals(fixture.expectedRegion(), trace.finalDecision().region(), fixture.name());
            }
            if (fixture.expectedServices() != null && !fixture.expectedServices().isEmpty()) {
                assertEquals(fixture.expectedServices(), trace.finalDecision().services(), fixture.name());
            }

            matchedCases.add(fixture.name() + " -> " + trace.finalDecision().type());
        }

        assertFalse(matchedCases.isEmpty());
    }

    @Test
    void broadAwsAccountAnalysisClearsPlannerInventedServiceSubset() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider("""
                {
                  "action": "use_tool",
                  "toolName": "aws_region_audit",
                  "arguments": {
                    "services": ["sts", "ec2"]
                  },
                  "missingFields": [],
                  "reason": "Broad AWS account analysis request."
                }
                """);
        ChatModelProviderRegistry registry = new ChatModelProviderRegistry(
                new net.jrodolfo.llm.config.AppModelProperties("ollama"),
                java.util.Map.of("ollama", chatModelProvider)
        );
        ToolDecisionService service = new ToolDecisionService(
                new AppToolsProperties("hybrid", false),
                new LlmToolPlannerService(registry, objectMapper),
                new ChatToolRouterService()
        );

        ToolDecisionService.DecisionTrace trace = service.routeDetailed(
                "Analyze my AWS account and summarize the services I am using, highlighting anything unusual or potentially worth reviewing.",
                "ollama",
                "llama3:8b"
        );

        assertEquals(ChatToolRouterService.DecisionType.AWS_REGION_AUDIT, trace.finalDecision().type());
        assertTrue(trace.finalDecision().services().isEmpty());
    }

    private PendingToolCall toPendingToolCall(PendingToolFixture pendingToolFixture) {
        return new PendingToolCall(
                ChatToolRouterService.DecisionType.valueOf(pendingToolFixture.type()),
                pendingToolFixture.reportType(),
                pendingToolFixture.bucket(),
                pendingToolFixture.region(),
                pendingToolFixture.days(),
                pendingToolFixture.reason(),
                pendingToolFixture.services() == null ? List.of() : pendingToolFixture.services(),
                List.of(),
                pendingToolFixture.missingFields() == null ? List.of() : pendingToolFixture.missingFields()
        );
    }

    private record EvaluationFixture(
            String name,
            String mode,
            String message,
            String plannerResponse,
            PendingToolFixture pendingTool,
            String expectedType,
            boolean expectedUseTool,
            boolean expectedClarification,
            boolean expectedFallbackUsed,
            String expectedBucket,
            String expectedRegion,
            List<String> expectedServices
    ) {
    }

    private record PendingToolFixture(
            String type,
            String reportType,
            String bucket,
            String region,
            Integer days,
            String reason,
            List<String> services,
            List<String> missingFields
    ) {
    }

    private static final class FakeChatModelProvider implements ChatModelProvider {
        private final String plannerResponse;

        private FakeChatModelProvider(String plannerResponse) {
            this.plannerResponse = plannerResponse;
        }

        @Override
        public ChatResponse chat(
                ProviderPrompt message,
                String model,
                net.jrodolfo.llm.dto.ChatToolMetadata toolMetadata,
                java.util.Map<String, Object> toolResult,
                String sessionId,
                net.jrodolfo.llm.dto.PendingToolCallResponse pendingTool
        ) {
            return new ChatResponse(plannerResponse, resolveModel(model), null, null, null, null, null);
        }

        @Override
        public net.jrodolfo.llm.provider.StreamingChatResult streamChat(ProviderPrompt message, String model, Consumer<String> tokenConsumer) {
            throw new UnsupportedOperationException("Not needed for evaluation tests.");
        }

        @Override
        public String resolveModel(String model) {
            return (model == null || model.isBlank()) ? "llama3:8b" : model;
        }
    }
}
