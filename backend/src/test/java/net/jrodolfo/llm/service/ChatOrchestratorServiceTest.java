package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jrodolfo.llm.client.McpClient;
import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.config.AppToolsProperties;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.config.McpProperties;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.McpToolInvocationResponse;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.ReadReportSummaryToolRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import net.jrodolfo.llm.model.PendingToolCall;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import net.jrodolfo.llm.provider.ProviderPrompt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOrchestratorServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void noToolRequestFallsBackToRegularChatAndPersistsSession() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(), sessionStore, "rules");

        ChatResponse response = orchestrator.chat("explain recursion", "ollama", "llama3:8b", null);

        assertEquals("plain response", response.response());
        assertEquals(2, chatModelProvider.lastMessages.size());
        assertEquals("system", chatModelProvider.lastMessages.get(0).role());
        assertEquals("user", chatModelProvider.lastMessages.get(1).role());
        assertEquals("explain recursion", chatModelProvider.lastMessages.get(1).content());
        assertTrue(chatModelProvider.lastPrompt.contains("User: explain recursion"));
        assertTrue(chatModelProvider.lastPrompt.contains("Assistant:"));
        assertFalse(chatModelProvider.lastPrompt.contains("<current_user_message>"));
        assertFalse(chatModelProvider.lastPrompt.contains("<tool_context>"));
        assertNull(response.tool());
        assertNotNull(response.sessionId());

        var storedSession = sessionStore.findById(response.sessionId()).orElseThrow();
        assertEquals(2, storedSession.messages().size());
        assertEquals("user", storedSession.messages().get(0).role());
        assertEquals("assistant", storedSession.messages().get(1).role());
    }

    @Test
    void followUpRequestIncludesConversationHistory() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(), sessionStore, "rules");

        ChatResponse firstResponse = orchestrator.chat("explain recursion", "ollama", "llama3:8b", null);
        ChatResponse secondResponse = orchestrator.chat("give me an example", "ollama", "llama3:8b", firstResponse.sessionId());

        assertEquals(firstResponse.sessionId(), secondResponse.sessionId());
        assertEquals("assistant", chatModelProvider.lastMessages.get(2).role());
        assertEquals("plain response", chatModelProvider.lastMessages.get(2).content());
        assertTrue(chatModelProvider.lastPrompt.contains("Conversation so far:"));
        assertTrue(chatModelProvider.lastPrompt.contains("User: explain recursion"));
        assertTrue(chatModelProvider.lastPrompt.contains("Assistant: plain response"));
        assertTrue(chatModelProvider.lastPrompt.contains("User: give me an example"));
        assertFalse(chatModelProvider.lastPrompt.contains("<conversation_history>"));
    }

    @Test
    void auditRequestUsesToolAddsMetadataAndPersistsAssistantToolState() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(), sessionStore, "rules");

        ChatResponse response = orchestrator.chat("run aws audit for us-east-2 sts", "ollama", "llama3:8b", null);

        assertNotNull(response.tool());
        assertTrue(response.tool().used());
        assertEquals("aws_region_audit", response.tool().name());
        assertEquals("audit_summary", response.toolResult().get("type"));
        assertTrue(chatModelProvider.lastPrompt.contains("<tool_context>"));
        assertTrue(chatModelProvider.lastPrompt.contains("tool_name: aws_region_audit"));

        var storedSession = sessionStore.findById(response.sessionId()).orElseThrow();
        assertEquals("aws_region_audit", storedSession.messages().get(1).tool().name());
        assertEquals("audit_summary", storedSession.messages().get(1).toolResult().get("type"));
    }

    @Test
    void awsAccountAnalysisRequestUsesAuditToolWithoutClarification() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse response = orchestrator.chat(
                "Analyze my AWS account and summarize the services I am using, highlighting anything unusual or potentially worth reviewing.",
                "ollama",
                "llama3:8b",
                null
        );

        assertNotNull(response.tool());
        assertEquals("aws_region_audit", response.tool().name());
        assertEquals("success", response.tool().status());
        assertNotNull(mcpService.lastAuditRequest);
        assertNull(mcpService.lastAuditRequest.services());
        assertTrue(chatModelProvider.lastPrompt.contains("<tool_context>"));
        assertTrue(chatModelProvider.lastPrompt.contains("tool_name: aws_region_audit"));
        assertFalse(response.response().toLowerCase().contains("account id"));
    }

    @Test
    void auditRequestMarksPartialSuccessWhenAuditSummaryContainsFailures() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new PartialAuditMcpService(), sessionStore, "rules");

        ChatResponse response = orchestrator.chat(
                "Analyze my AWS account and summarize the services I am using, highlighting anything unusual or potentially worth reviewing.",
                "ollama",
                "llama3:8b",
                null
        );

        assertEquals("partial-success", response.tool().status());
        assertEquals("partial-success", response.toolResult().get("status"));
        assertEquals("EC2 instances - us-east-1", ((Map<?, ?>) ((List<?>) response.toolResult().get("failedSteps")).get(0)).get("step"));
        assertTrue(response.tool().summary().contains("completed with failures"));
    }

    @Test
    void toolFailureReturnsExplicitFailureResponseAndPersistsIt() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new ErrorMcpService(), sessionStore, "rules");

        ChatResponse response = orchestrator.chat("run aws audit for us-east-2 sts", "ollama", "llama3:8b", null);

        assertTrue(response.response().contains("I tried to use the local tool"));
        assertNotNull(response.tool());
        assertEquals("failed", response.tool().status());
        assertFalse(chatModelProvider.generateCalled);

        var storedSession = sessionStore.findById(response.sessionId()).orElseThrow();
        assertEquals("failed", storedSession.messages().get(1).tool().status());
    }

    @Test
    void structuredMcpToolErrorReturnsFailureInsteadOfProviderPrompt() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new StructuredErrorMcpService(), sessionStore, "rules");

        ChatResponse response = orchestrator.chat(
                "Analyze my AWS account and summarize the services I am using, highlighting anything unusual or potentially worth reviewing.",
                "ollama",
                "llama3:8b",
                null
        );

        assertTrue(response.response().contains("I tried to use the local tool `aws_region_audit`, but it failed"));
        assertTrue(response.response().contains("AWS region audit requires aws CLI and jq in the backend runtime."));
        assertNotNull(response.tool());
        assertEquals("failed", response.tool().status());
        assertFalse(chatModelProvider.generateCalled);
        assertNull(response.toolResult());
    }

    @Test
    void clarificationRequestReturnsImmediateClarificationResponse() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(), sessionStore, "rules");

        ChatResponse response = orchestrator.chat("check bucket metrics for the last 7 days", "ollama", "llama3:8b", null);

        assertTrue(response.response().contains("I can run an S3 CloudWatch report using your local AWS CLI credentials."));
        assertTrue(response.response().contains("one bucket at a time"));
        assertNotNull(response.tool());
        assertEquals("clarification-needed", response.tool().status());
        assertFalse(chatModelProvider.generateCalled);
        assertNotNull(response.sessionId());
        PendingToolCall pendingToolCall = sessionStore.findById(response.sessionId()).orElseThrow().pendingToolCall();
        assertNotNull(pendingToolCall);
        assertEquals(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, pendingToolCall.type());
    }

    @Test
    void genericS3ReportRequestReturnsCredentialAwareClarificationAndPreservesDays() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(), sessionStore, "rules");

        ChatResponse response = orchestrator.chat("Give me a report from AWS S3 for the last month.", "ollama", "llama3:8b", null);

        assertTrue(response.response().contains("local AWS CLI credentials"));
        assertTrue(response.response().contains("one bucket at a time"));
        assertTrue(response.response().contains("Please provide the bucket name"));
        assertTrue(response.response().contains("ask me: \"list my S3 buckets\""));
        assertFalse(response.response().contains("run an AWS audit"));
        assertFalse(response.response().toLowerCase().contains("account id"));
        assertFalse(response.response().toLowerCase().contains("username"));
        assertFalse(response.response().contains("Do you want all accessible buckets"));
        assertEquals("clarification-needed", response.tool().status());
        PendingToolCall pendingToolCall = sessionStore.findById(response.sessionId()).orElseThrow().pendingToolCall();
        assertEquals(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, pendingToolCall.type());
        assertEquals(30, pendingToolCall.days());
    }

    @Test
    void ambiguousLatestReportRequestReturnsClarificationResponse() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(), sessionStore, "rules");

        ChatResponse response = orchestrator.chat("read the latest report", "ollama", "llama3:8b", null);

        assertTrue(response.response().contains("latest audit report or the latest s3 cloudwatch report"));
        assertNotNull(response.tool());
        assertEquals("clarification-needed", response.tool().status());
        assertFalse(chatModelProvider.generateCalled);
        PendingToolCall pendingToolCall = sessionStore.findById(response.sessionId()).orElseThrow().pendingToolCall();
        assertNotNull(pendingToolCall);
        assertEquals(ChatToolRouterService.DecisionType.READ_LATEST_REPORT, pendingToolCall.type());
    }

    @Test
    void bucketFollowUpUsesPendingClarificationState() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse clarification = orchestrator.chat("check bucket metrics for the last 7 days", "ollama", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("jrodolfo.net", "ollama", "llama3:8b", clarification.sessionId());

        assertEquals("jrodolfo.net", mcpService.lastS3Request.bucket());
        assertEquals(7, mcpService.lastS3Request.days());
        assertEquals("success", followUp.tool().status());
        assertNull(sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall());
        assertFalse(chatModelProvider.generateCalled);
        assertTrue(followUp.response().contains("S3 CloudWatch report completed for bucket `jrodolfo.net`."));
    }

    @Test
    void completedS3ReportUsesDeterministicImmediateResponse() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextAssistantResponse = """
                Based on the S3 CloudWatch report for your bucket `jrodolfo.net` with success, here's a summary.

                As you've requested, I will proceed with running an S3 report for `jrodolfo.net` for the last month.
                """;
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse response = orchestrator.chat(
                "Yes, please, run an S3 report for jrodolfo.net for the last month.",
                "ollama",
                "llama3:8b",
                null
        );

        assertEquals("jrodolfo.net", mcpService.lastS3Request.bucket());
        assertEquals(30, mcpService.lastS3Request.days());
        assertFalse(chatModelProvider.generateCalled);
        assertTrue(response.response().contains("S3 CloudWatch report completed for bucket `jrodolfo.net`."));
        assertTrue(response.response().contains("success_count=5"));
        assertTrue(response.response().contains("failure_count=0"));
        assertTrue(response.response().contains("Artifacts are available in the tool result card."));
        assertFalse(response.response().contains("will proceed"));
        assertFalse(response.response().contains("/app/"));

        var storedSession = sessionStore.findById(response.sessionId()).orElseThrow();
        assertEquals(response.response(), storedSession.messages().get(1).content());
    }

    @Test
    void streamedCompletedS3ReportPersistsCorrectedResponse() {
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(new FakeChatModelProvider(), new FakeMcpService(), sessionStore, "rules");

        ChatOrchestratorService.PreparedChat preparedChat = orchestrator.prepareChat(
                "run an S3 report for jrodolfo.net for the last month",
                "ollama",
                "llama3:8b",
                null
        );
        var persistedSession = orchestrator.completePreparedChat(
                preparedChat,
                "As you've requested, I will proceed with running an S3 report for `jrodolfo.net` for the last month.",
                new ModelProviderMetadata("ollama", "llama3:8b", null, null, null, null, null, null, null, null)
        );

        String persistedResponse = persistedSession.messages().get(1).content();
        assertTrue(persistedResponse.contains("S3 CloudWatch report completed for bucket `jrodolfo.net`."));
        assertTrue(persistedResponse.contains("Artifacts are available in the tool result card."));
        assertFalse(persistedResponse.contains("will proceed"));
    }

    @Test
    void completedS3ReportReplacesAwkwardPathHeavyResponse() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextAssistantResponse = """
                The S3 CloudWatch report for the jrodolfo.net bucket has already been completed with success.
                Additionally, you can view the report in the /app/agents/reports/s3-cloudwatch/s3-cloudwatch-2026-07-17_12-23-36/report.txt file.

                Considering this, I suggest that you run an S3 report for one of these buckets, such as jrodolfo-audio, for the last month.
                """;
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse response = orchestrator.chat(
                "Please run an S3 report for the jrodolfo.net bucket.",
                "ollama",
                "llama3:8b",
                null
        );

        assertTrue(response.response().contains("S3 CloudWatch report completed for bucket `jrodolfo.net`."));
        assertTrue(response.response().contains("Artifacts are available in the tool result card."));
        assertFalse(response.response().contains("already been completed"));
        assertFalse(response.response().contains("/app/"));
        assertFalse(response.response().contains("suggest that you run an S3 report"));
    }

    @Test
    void allBucketsFollowUpReturnsCurrentBoundaryWithoutCallingProvider() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse clarification = orchestrator.chat("Give me a report from AWS S3 for the last month.", "ollama", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("all buckets", "ollama", "llama3:8b", clarification.sessionId());

        assertTrue(followUp.response().contains("All-bucket S3 CloudWatch reports are not implemented yet."));
        assertTrue(followUp.response().contains("ask me: \"list my S3 buckets\""));
        assertFalse(followUp.response().contains("run an AWS audit"));
        assertEquals("clarification-needed", followUp.tool().status());
        assertFalse(chatModelProvider.generateCalled);
        assertNull(mcpService.lastS3Request);
        assertEquals(30, sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall().days());
    }

    @Test
    void s3BucketListingAddsBucketNamesToPromptAndToolResult() throws Exception {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        Path runDir = tempDir.resolve("reports").resolve("audit").resolve("aws-audit-2026-06-05_14-14-12");
        Files.createDirectories(runDir.resolve("json"));
        Files.writeString(runDir.resolve("json").resolve("s3_list_buckets.json"), """
                [
                  {"Name": "first-bucket", "CreationDate": "2026-01-01T00:00:00Z"},
                  {"Name": "second-bucket", "CreationDate": "2026-01-02T00:00:00Z"}
                ]
                """);
        FakeMcpService mcpService = new FakeMcpService(runDir.toString());
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse response = orchestrator.chat("list my S3 buckets", "ollama", "llama3:8b", null);

        assertEquals("aws_region_audit", response.tool().name());
        assertEquals("S3 bucket discovery completed with bucket_count=2.", response.tool().summary());
        assertEquals(List.of("first-bucket", "second-bucket"), response.toolResult().get("bucketNames"));
        assertTrue(chatModelProvider.lastPrompt.contains("\"bucketNames\""));
        assertTrue(chatModelProvider.lastPrompt.contains("first-bucket"));
        assertTrue(chatModelProvider.lastPrompt.contains("run an S3 report for <bucket-name> for the last month"));
    }

    @Test
    void auditResponseRecommendationCreatesPendingS3FollowUpState() throws Exception {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextAssistantResponse = """
                Given that you already have a comprehensive analysis of your AWS account, I recommend taking the next step by running an S3 report for one of your bucket names for the last month.
                Please let me know if you'd like to proceed with this recommendation.
                """;
        FileChatSessionStore sessionStore = newSessionStore();
        Path runDir = auditRunDirWithBuckets("first-bucket", "second-bucket");
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(runDir.toString()), sessionStore, "rules");

        ChatResponse response = orchestrator.chat(
                "Analyze my AWS account and summarize the services I am using, highlighting anything unusual or potentially worth reviewing.",
                "ollama",
                "llama3:8b",
                null
        );

        assertNotNull(response.pendingTool());
        assertEquals("s3_cloudwatch_report", response.pendingTool().toolName());
        assertEquals(30, response.pendingTool().days());
        assertEquals(List.of("first-bucket", "second-bucket"), response.pendingTool().bucketOptions());
        PendingToolCall pendingToolCall = sessionStore.findById(response.sessionId()).orElseThrow().pendingToolCall();
        assertNotNull(pendingToolCall);
        assertEquals(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, pendingToolCall.type());
        assertEquals(List.of("first-bucket", "second-bucket"), pendingToolCall.bucketOptions());
    }

    @Test
    void affirmativeFollowUpToRecommendedS3ReportWithMultipleBucketsAsksForBucketSelection() throws Exception {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextAssistantResponse = "I recommend taking the next step by running an S3 report for one of your bucket names for the last month. Please let me know if you'd like to proceed.";
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService(auditRunDirWithBuckets("first-bucket", "second-bucket").toString());
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse firstResponse = orchestrator.chat("Analyze my AWS account and summarize the services I am using.", "ollama", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("Yes, please proceed with the recommendation.", "ollama", "llama3:8b", firstResponse.sessionId());

        assertTrue(followUp.response().contains("I can proceed with the S3 CloudWatch report"));
        assertTrue(followUp.response().contains("first-bucket, second-bucket"));
        assertEquals("clarification-needed", followUp.tool().status());
        assertNull(mcpService.lastS3Request);
        assertEquals(List.of("first-bucket", "second-bucket"), sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall().bucketOptions());
    }

    @Test
    void affirmativeFollowUpToRecommendedS3ReportWithOneBucketRunsReport() throws Exception {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextAssistantResponse = "I recommend taking the next step by running an S3 report for one of your bucket names for the last month. Please let me know if you'd like to proceed.";
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService(auditRunDirWithBuckets("only-bucket").toString());
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse firstResponse = orchestrator.chat("Analyze my AWS account and summarize the services I am using.", "ollama", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("Yes, please proceed with the recommendation.", "ollama", "llama3:8b", firstResponse.sessionId());

        assertEquals("only-bucket", mcpService.lastS3Request.bucket());
        assertEquals(30, mcpService.lastS3Request.days());
        assertEquals("success", followUp.tool().status());
        assertNull(sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall());
    }

    @Test
    void topicChangeAfterRecommendedS3ReportFallsBackToRegularChat() throws Exception {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextAssistantResponse = "I recommend taking the next step by running an S3 report for one of your bucket names for the last month. Please let me know if you'd like to proceed.";
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService(auditRunDirWithBuckets("first-bucket", "second-bucket").toString());
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse firstResponse = orchestrator.chat("Analyze my AWS account and summarize the services I am using.", "ollama", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("explain recursion", "ollama", "llama3:8b", firstResponse.sessionId());

        assertEquals("plain response", followUp.response());
        assertNull(followUp.tool());
        assertNull(mcpService.lastS3Request);
        assertNull(sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall());
    }

    @Test
    void reportTypeFollowUpUsesPendingClarificationState() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "rules");

        ChatResponse clarification = orchestrator.chat("read the latest report", "ollama", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("audit", "ollama", "llama3:8b", clarification.sessionId());

        assertEquals("audit", mcpService.lastListReportsRequest.reportType());
        assertEquals("/tmp/report-1", mcpService.lastReadReportSummaryRequest.runDir());
        assertEquals("success", followUp.tool().status());
        assertNull(sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall());
    }

    @Test
    void unrelatedFollowUpFallsBackToRegularChat() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(), sessionStore, "rules");

        ChatResponse clarification = orchestrator.chat("check bucket metrics for the last 7 days", "ollama", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("explain recursion", "ollama", "llama3:8b", clarification.sessionId());

        assertEquals("plain response", followUp.response());
        assertNull(followUp.tool());
        assertNull(sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall());
    }

    @Test
    void completePreparedChatPersistsStreamedAssistantResponse() {
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(new FakeChatModelProvider(), new FakeMcpService(), sessionStore, "rules");

        ChatOrchestratorService.PreparedChat preparedChat = orchestrator.prepareChat("explain recursion", "ollama", "llama3:8b", null);
        var persistedSession = orchestrator.completePreparedChat(
                preparedChat,
                "streamed response",
                new ModelProviderMetadata("ollama", "llama3:8b", null, null, null, null, null, null, null, null)
        );

        assertEquals(2, persistedSession.messages().size());
        assertEquals("streamed response", persistedSession.messages().get(1).content());
        assertEquals("ollama", persistedSession.messages().get(1).metadata().provider());
        assertEquals(persistedSession.sessionId(), sessionStore.findById(persistedSession.sessionId()).orElseThrow().sessionId());
    }

    @Test
    void hybridModeUsesPlannerDecisionWhenItReturnsValidToolJson() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextPlannerResponse = """
                {
                  "action": "use_tool",
                  "toolName": "aws_region_audit",
                  "arguments": {
                    "region": "us-east-2",
                    "services": ["sts"]
                  },
                  "missingFields": [],
                  "reason": "User explicitly asked for an AWS audit."
                }
                """;
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(), sessionStore, "hybrid");

        ChatResponse response = orchestrator.chat("please audit my aws account in us-east-2 with sts", "ollama", "llama3:8b", null);

        assertNotNull(response.tool());
        assertEquals("aws_region_audit", response.tool().name());
        assertEquals(1, chatModelProvider.plannerCalls);
        assertTrue(chatModelProvider.lastPrompt.contains("tool_name: aws_region_audit"));
    }

    @Test
    void hybridModeSkipsPlannerForObviousPlainChatPrompt() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, new FakeMcpService(), sessionStore, "hybrid");

        ChatResponse response = orchestrator.chat("Translate \"Good morning!\" to French.", "ollama", "llama3:8b", null);

        assertEquals("plain response", response.response());
        assertNull(response.tool());
        assertEquals(0, chatModelProvider.plannerCalls);
        assertEquals("user", chatModelProvider.lastMessages.get(1).role());
        assertEquals("Translate \"Good morning!\" to French.", chatModelProvider.lastMessages.get(1).content());
    }

    @Test
    void hybridModeFallsBackToRulesWhenPlannerOutputIsMalformed() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextPlannerResponse = "not valid json";
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "hybrid");

        ChatResponse response = orchestrator.chat("run aws audit for us-east-2 sts", "ollama", "llama3:8b", null);

        assertNotNull(response.tool());
        assertEquals("aws_region_audit", response.tool().name());
        assertEquals(1, chatModelProvider.plannerCalls);
    }

    @Test
    void hybridModeFallsBackToRuleBasedAuditWhenPlannerAsksForGenericClarification() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextPlannerResponse = """
                {
                  "action": "clarification_needed",
                  "toolName": "aws_region_audit",
                  "arguments": {},
                  "missingFields": [],
                  "reason": "Need more information before running the tool."
                }
                """;
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "hybrid");

        ChatResponse response = orchestrator.chat("Please audit my AWS account.", "ollama", "llama3:8b", null);

        assertNotNull(response.tool());
        assertEquals("aws_region_audit", response.tool().name());
        assertEquals("success", response.tool().status());
        assertEquals(1, chatModelProvider.plannerCalls);
    }

    @Test
    void hybridModeClearsPlannerInventedServiceSubsetForBroadAwsAccountAnalysis() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextPlannerResponse = """
                {
                  "action": "use_tool",
                  "toolName": "aws_region_audit",
                  "arguments": {
                    "services": ["sts", "ec2"]
                  },
                  "missingFields": [],
                  "reason": "Broad AWS account analysis request."
                }
                """;
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "hybrid");

        ChatResponse response = orchestrator.chat(
                "Analyze my AWS account and summarize the services I am using, highlighting anything unusual or potentially worth reviewing.",
                "ollama",
                "llama3:8b",
                null
        );

        assertNotNull(response.tool());
        assertEquals("aws_region_audit", response.tool().name());
        assertEquals(1, chatModelProvider.plannerCalls);
        assertEquals(List.of(), mcpService.lastAuditRequest.services());
    }

    @Test
    void hybridModeUsesPlannerForPendingClarificationFollowUp() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextPlannerResponse = """
                {
                  "action": "clarification_needed",
                  "toolName": "s3_cloudwatch_report",
                  "arguments": {
                    "days": 7
                  },
                  "missingFields": ["bucket"],
                  "reason": "Bucket metrics request missing the bucket name."
                }
                """;
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "hybrid");

        ChatResponse clarification = orchestrator.chat("check s3 cloudwatch metrics for the last 7 days", "ollama", "llama3:8b", null);
        chatModelProvider.enqueuePlannerResponse("""
                {
                  "action": "none",
                  "toolName": null,
                  "arguments": {},
                  "missingFields": [],
                  "reason": "The follow-up message alone is not enough to infer a standalone tool."
                }
                """);
        chatModelProvider.enqueuePlannerResponse("""
                {
                  "action": "use_tool",
                  "toolName": "s3_cloudwatch_report",
                  "arguments": {
                    "bucket": "jrodolfo.net",
                    "days": 7
                  },
                  "missingFields": [],
                  "reason": "Follow-up provides the missing bucket."
                }
                """);

        ChatResponse followUp = orchestrator.chat("jrodolfo.net", "ollama", "llama3:8b", clarification.sessionId());

        assertEquals("jrodolfo.net", mcpService.lastS3Request.bucket());
        assertEquals("success", followUp.tool().status());
        assertEquals(2, chatModelProvider.plannerCalls);
    }

    @Test
    void hybridModeFallsBackToRuleBasedPendingResolutionWhenPlannerRepeatsClarification() {
        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();
        chatModelProvider.nextPlannerResponse = """
                {
                  "action": "clarification_needed",
                  "toolName": "s3_cloudwatch_report",
                  "arguments": {
                    "days": 7
                  },
                  "missingFields": ["bucket"],
                  "reason": "Bucket metrics request missing the bucket name."
                }
                """;
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(chatModelProvider, mcpService, sessionStore, "hybrid");

        ChatResponse clarification = orchestrator.chat("check s3 cloudwatch metrics for the last 7 days", "ollama", "llama3:8b", null);
        chatModelProvider.enqueuePlannerResponse("""
                {
                  "action": "clarification_needed",
                  "toolName": "s3_cloudwatch_report",
                  "arguments": {
                    "days": 7
                  },
                  "missingFields": ["bucket"],
                  "reason": "The follow-up still does not provide the bucket."
                }
                """);

        ChatResponse followUp = orchestrator.chat("jrodolfo.net", "ollama", "llama3:8b", clarification.sessionId());

        assertEquals("jrodolfo.net", mcpService.lastS3Request.bucket());
        assertEquals("success", followUp.tool().status());
        assertNull(sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall());
    }

    private FileChatSessionStore newSessionStore() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new FileChatSessionStore(objectMapper, new AppStorageProperties(tempDir.resolve("sessions").toString(), tempDir.resolve("reports").toString()), new SessionIdPolicy());
    }

    private Path auditRunDirWithBuckets(String... bucketNames) throws Exception {
        Path runDir = tempDir.resolve("reports").resolve("audit").resolve("aws-audit-test-" + System.nanoTime());
        Files.createDirectories(runDir.resolve("json"));
        StringBuilder bucketsJson = new StringBuilder("[\n");
        for (int index = 0; index < bucketNames.length; index++) {
            if (index > 0) {
                bucketsJson.append(",\n");
            }
            bucketsJson.append("  {\"Name\": \"").append(bucketNames[index]).append("\", \"CreationDate\": \"2026-01-01T00:00:00Z\"}");
        }
        bucketsJson.append("\n]\n");
        Files.writeString(runDir.resolve("json").resolve("s3_list_buckets.json"), bucketsJson.toString());
        return runDir;
    }

    private Path auditRunDirWithEmptyBucketArtifact() throws Exception {
        Path runDir = tempDir.resolve("reports").resolve("audit").resolve("aws-audit-test-" + System.nanoTime());
        Files.createDirectories(runDir.resolve("json"));
        Files.writeString(runDir.resolve("json").resolve("s3_list_buckets.json"), "");
        return runDir;
    }

    private ChatOrchestratorService newOrchestrator(
            ChatModelProvider chatModelProvider,
            McpService mcpService,
            FileChatSessionStore sessionStore,
            String routingMode
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        ChatModelProviderRegistry chatModelProviderRegistry = new ChatModelProviderRegistry(
                new net.jrodolfo.llm.config.AppModelProperties("ollama"),
                Map.of("ollama", chatModelProvider)
        );
        return new ChatOrchestratorService(
                chatModelProviderRegistry,
                mcpService,
                new ToolDecisionService(
                        new AppToolsProperties(routingMode, false),
                        new LlmToolPlannerService(chatModelProviderRegistry, objectMapper),
                        new ChatToolRouterService()
                ),
                new ChatMemoryService(sessionStore, new ChatSessionMetadataService(), new SessionIdPolicy()),
                new ChatPromptBuilder(objectMapper),
                new ChatSessionService(sessionStore, new ChatSessionMetadataService()),
                new AppStorageProperties(tempDir.resolve("sessions").toString(), tempDir.resolve("reports").toString())
        );
    }

    private static final class FakeChatModelProvider implements ChatModelProvider {
        private String lastPrompt;
        private java.util.List<net.jrodolfo.llm.provider.ProviderPromptMessage> lastMessages = java.util.List.of();
        private boolean generateCalled;
        private String nextAssistantResponse;
        private String nextPlannerResponse;
        private int plannerCalls;
        private final java.util.ArrayDeque<String> plannerResponses = new java.util.ArrayDeque<>();

        @Override
        public ChatResponse chat(
                ProviderPrompt message,
                String model,
                net.jrodolfo.llm.dto.ChatToolMetadata toolMetadata,
                java.util.Map<String, Object> toolResult,
                String sessionId,
                net.jrodolfo.llm.dto.PendingToolCallResponse pendingTool
        ) {
            if (message.prompt() != null && message.prompt().contains("<tool_planning_request>")) {
                this.plannerCalls++;
                String plannerResponse;
                if (!plannerResponses.isEmpty()) {
                    plannerResponse = plannerResponses.removeFirst();
                } else if (nextPlannerResponse != null) {
                    plannerResponse = nextPlannerResponse;
                    nextPlannerResponse = null;
                } else {
                    plannerResponse = "{\"action\":\"none\",\"toolName\":null,\"arguments\":{},\"missingFields\":[],\"reason\":\"No supported tool is required.\"}";
                }
                return new ChatResponse(plannerResponse, resolveModel(model), null, null, null, null, null);
            }
            this.lastPrompt = message.prompt();
            this.lastMessages = message.messages();
            this.generateCalled = true;
            String response = nextAssistantResponse != null ? nextAssistantResponse : "plain response";
            nextAssistantResponse = null;
            return new ChatResponse(response, resolveModel(model), toolMetadata, toolResult, sessionId, pendingTool, null);
        }

        @Override
        public net.jrodolfo.llm.provider.StreamingChatResult streamChat(ProviderPrompt message, String model, java.util.function.Consumer<String> tokenConsumer) {
            throw new UnsupportedOperationException("Not needed for this test.");
        }

        @Override
        public String resolveModel(String model) {
            return (model == null || model.isBlank()) ? "llama3:8b" : model;
        }

        private void enqueuePlannerResponse(String plannerResponse) {
            this.plannerResponses.addLast(plannerResponse);
        }
    }

    private static class FakeMcpService extends McpService {
        private AwsRegionAuditToolRequest lastAuditRequest;
        private S3CloudwatchReportToolRequest lastS3Request;
        private ListReportsRequest lastListReportsRequest;
        private ReadReportSummaryToolRequest lastReadReportSummaryRequest;
        private final String auditRunDir;

        private FakeMcpService() {
            this(null);
        }

        private FakeMcpService(String auditRunDir) {
            super(new FakeMcpClient(), new McpProperties(true, "node", List.of(), ".", 5, 30));
            this.auditRunDir = auditRunDir;
        }

        @Override
        public McpToolInvocationResponse runAwsRegionAudit(AwsRegionAuditToolRequest request) {
            this.lastAuditRequest = request;
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("ok", true);
            result.put("summary", Map.of(
                    "success_count", 1,
                    "failure_count", 0,
                    "skipped_count", 19
            ));
            if (auditRunDir != null) {
                result.put("run_dir", auditRunDir);
            }
            return new McpToolInvocationResponse("aws_region_audit", result);
        }

        @Override
        public McpToolInvocationResponse runS3CloudwatchReport(S3CloudwatchReportToolRequest request) {
            this.lastS3Request = request;
            return new McpToolInvocationResponse("s3_cloudwatch_report", Map.of(
                    "ok", true,
                    "run_dir", "s3-cloudwatch/fake-run",
                    "summary", Map.of(
                            "bucket", request.bucket(),
                            "success_count", 5,
                            "failure_count", 0
                    )
            ));
        }

        @Override
        public McpToolInvocationResponse listRecentReports(ListReportsRequest request) {
            this.lastListReportsRequest = request;
            return new McpToolInvocationResponse("list_recent_reports", Map.of(
                    "ok", true,
                    "reports", List.of(Map.of("run_dir", "/tmp/report-1"))
            ));
        }

        @Override
        public McpToolInvocationResponse readReportSummary(ReadReportSummaryToolRequest request) {
            this.lastReadReportSummaryRequest = request;
            return new McpToolInvocationResponse("read_report_summary", Map.of(
                    "ok", true,
                    "report_type", "audit",
                    "summary", Map.of(
                            "success_count", 1,
                            "failure_count", 0
                    )
            ));
        }
    }

    private static class ErrorMcpService extends FakeMcpService {
        @Override
        public McpToolInvocationResponse runAwsRegionAudit(AwsRegionAuditToolRequest request) {
            throw new McpClientException("simulated failure");
        }
    }

    private static class StructuredErrorMcpService extends FakeMcpService {
        @Override
        public McpToolInvocationResponse runAwsRegionAudit(AwsRegionAuditToolRequest request) {
            throw new McpClientException("AWS region audit requires aws CLI and jq in the backend runtime.");
        }
    }

    private final class PartialAuditMcpService extends FakeMcpService {
        private PartialAuditMcpService() {
            super();
        }

        @Override
        public McpToolInvocationResponse runAwsRegionAudit(AwsRegionAuditToolRequest request) {
            Path runDir;
            try {
                runDir = auditRunDirWithEmptyBucketArtifact();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return new McpToolInvocationResponse("aws_region_audit", Map.of(
                    "ok", true,
                    "run_dir", runDir.toString(),
                    "summary", Map.of(
                            "success_count", 1,
                            "failure_count", 2,
                            "skipped_count", 0,
                            "failed_commands", List.of(
                                    Map.of(
                                            "scope", "us-east-1",
                                            "service", "ec2",
                                            "title", "EC2 instances - us-east-1",
                                            "stderr_path", tempDir.resolve("reports").resolve("audit").resolve("stderr").resolve("ec2.stderr").toString()
                                    )
                            )
                    )
            ));
        }
    }

    private static final class FakeMcpClient extends McpClient {
        private FakeMcpClient() {
            super(new ObjectMapper(), new McpProperties(true, "node", List.of(), ".", 5, 30));
        }
    }
}
