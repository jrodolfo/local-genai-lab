package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jrodolfo.llm.client.McpClient;
import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.config.McpProperties;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.McpToolInvocationResponse;
import net.jrodolfo.llm.dto.ReadReportSummaryToolRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import net.jrodolfo.llm.model.PendingToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        FakeOllamaService ollamaService = new FakeOllamaService();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(ollamaService, new FakeMcpService(), sessionStore);

        ChatResponse response = orchestrator.chat("explain recursion", "llama3:8b", null);

        assertEquals("plain response", response.response());
        assertTrue(ollamaService.lastPrompt.contains("<current_user_message>"));
        assertNull(response.tool());
        assertNotNull(response.sessionId());

        var storedSession = sessionStore.findById(response.sessionId()).orElseThrow();
        assertEquals(2, storedSession.messages().size());
        assertEquals("user", storedSession.messages().get(0).role());
        assertEquals("assistant", storedSession.messages().get(1).role());
    }

    @Test
    void followUpRequestIncludesConversationHistory() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(ollamaService, new FakeMcpService(), sessionStore);

        ChatResponse firstResponse = orchestrator.chat("explain recursion", "llama3:8b", null);
        ChatResponse secondResponse = orchestrator.chat("give me an example", "llama3:8b", firstResponse.sessionId());

        assertEquals(firstResponse.sessionId(), secondResponse.sessionId());
        assertTrue(ollamaService.lastPrompt.contains("<conversation_history>"));
        assertTrue(ollamaService.lastPrompt.contains("user: explain recursion"));
        assertTrue(ollamaService.lastPrompt.contains("assistant: plain response"));
    }

    @Test
    void auditRequestUsesToolAddsMetadataAndPersistsAssistantToolState() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(ollamaService, new FakeMcpService(), sessionStore);

        ChatResponse response = orchestrator.chat("run aws audit for us-east-2 sts", "llama3:8b", null);

        assertNotNull(response.tool());
        assertTrue(response.tool().used());
        assertEquals("aws_region_audit", response.tool().name());
        assertTrue(ollamaService.lastPrompt.contains("<tool_context>"));
        assertTrue(ollamaService.lastPrompt.contains("tool_name: aws_region_audit"));

        var storedSession = sessionStore.findById(response.sessionId()).orElseThrow();
        assertEquals("aws_region_audit", storedSession.messages().get(1).tool().name());
    }

    @Test
    void toolFailureReturnsExplicitFailureResponseAndPersistsIt() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(ollamaService, new ErrorMcpService(), sessionStore);

        ChatResponse response = orchestrator.chat("run aws audit for us-east-2 sts", "llama3:8b", null);

        assertTrue(response.response().contains("I tried to use the local tool"));
        assertNotNull(response.tool());
        assertEquals("failed", response.tool().status());
        assertFalse(ollamaService.generateCalled);

        var storedSession = sessionStore.findById(response.sessionId()).orElseThrow();
        assertEquals("failed", storedSession.messages().get(1).tool().status());
    }

    @Test
    void clarificationRequestReturnsImmediateClarificationResponse() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(ollamaService, new FakeMcpService(), sessionStore);

        ChatResponse response = orchestrator.chat("check bucket metrics for the last 7 days", "llama3:8b", null);

        assertTrue(response.response().contains("I can run the S3 CloudWatch report, but I need the bucket name."));
        assertNotNull(response.tool());
        assertEquals("clarification-needed", response.tool().status());
        assertFalse(ollamaService.generateCalled);
        assertNotNull(response.sessionId());
        PendingToolCall pendingToolCall = sessionStore.findById(response.sessionId()).orElseThrow().pendingToolCall();
        assertNotNull(pendingToolCall);
        assertEquals(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, pendingToolCall.type());
    }

    @Test
    void ambiguousLatestReportRequestReturnsClarificationResponse() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(ollamaService, new FakeMcpService(), sessionStore);

        ChatResponse response = orchestrator.chat("read the latest report", "llama3:8b", null);

        assertTrue(response.response().contains("latest audit report or the latest s3 cloudwatch report"));
        assertNotNull(response.tool());
        assertEquals("clarification-needed", response.tool().status());
        assertFalse(ollamaService.generateCalled);
        PendingToolCall pendingToolCall = sessionStore.findById(response.sessionId()).orElseThrow().pendingToolCall();
        assertNotNull(pendingToolCall);
        assertEquals(ChatToolRouterService.DecisionType.READ_LATEST_REPORT, pendingToolCall.type());
    }

    @Test
    void bucketFollowUpUsesPendingClarificationState() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(ollamaService, mcpService, sessionStore);

        ChatResponse clarification = orchestrator.chat("check bucket metrics for the last 7 days", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("jrodolfo.net", "llama3:8b", clarification.sessionId());

        assertEquals("jrodolfo.net", mcpService.lastS3Request.bucket());
        assertEquals("success", followUp.tool().status());
        assertNull(sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall());
        assertTrue(ollamaService.lastPrompt.contains("tool_name: s3_cloudwatch_report"));
    }

    @Test
    void reportTypeFollowUpUsesPendingClarificationState() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        FileChatSessionStore sessionStore = newSessionStore();
        FakeMcpService mcpService = new FakeMcpService();
        ChatOrchestratorService orchestrator = newOrchestrator(ollamaService, mcpService, sessionStore);

        ChatResponse clarification = orchestrator.chat("read the latest report", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("audit", "llama3:8b", clarification.sessionId());

        assertEquals("audit", mcpService.lastListReportsRequest.reportType());
        assertEquals("/tmp/report-1", mcpService.lastReadReportSummaryRequest.runDir());
        assertEquals("success", followUp.tool().status());
        assertNull(sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall());
    }

    @Test
    void unrelatedFollowUpFallsBackToRegularChat() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(ollamaService, new FakeMcpService(), sessionStore);

        ChatResponse clarification = orchestrator.chat("check bucket metrics for the last 7 days", "llama3:8b", null);
        ChatResponse followUp = orchestrator.chat("explain recursion", "llama3:8b", clarification.sessionId());

        assertEquals("plain response", followUp.response());
        assertNull(followUp.tool());
        assertNull(sessionStore.findById(followUp.sessionId()).orElseThrow().pendingToolCall());
    }

    @Test
    void completePreparedChatPersistsStreamedAssistantResponse() {
        FileChatSessionStore sessionStore = newSessionStore();
        ChatOrchestratorService orchestrator = newOrchestrator(new FakeOllamaService(), new FakeMcpService(), sessionStore);

        ChatOrchestratorService.PreparedChat preparedChat = orchestrator.prepareChat("explain recursion", "llama3:8b", null);
        var persistedSession = orchestrator.completePreparedChat(preparedChat, "streamed response");

        assertEquals(2, persistedSession.messages().size());
        assertEquals("streamed response", persistedSession.messages().get(1).content());
        assertEquals(persistedSession.sessionId(), sessionStore.findById(persistedSession.sessionId()).orElseThrow().sessionId());
    }

    private FileChatSessionStore newSessionStore() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new FileChatSessionStore(objectMapper, new AppStorageProperties(tempDir.resolve("sessions").toString()));
    }

    private ChatOrchestratorService newOrchestrator(
            FakeOllamaService ollamaService,
            McpService mcpService,
            FileChatSessionStore sessionStore
    ) {
        return new ChatOrchestratorService(
                ollamaService,
                mcpService,
                new ChatToolRouterService(),
                new ChatMemoryService(sessionStore),
                new ChatPromptBuilder(new ObjectMapper()),
                new ChatSessionService(sessionStore)
        );
    }

    private static final class FakeOllamaService extends OllamaService {
        private String lastPrompt;
        private boolean generateCalled;

        private FakeOllamaService() {
            super(new net.jrodolfo.llm.client.OllamaClient(new ObjectMapper(), new net.jrodolfo.llm.config.OllamaProperties(
                    "http://localhost:11434",
                    "llama3:8b",
                    10,
                    10
            )));
        }

        @Override
        public ChatResponse chat(String message, String model, net.jrodolfo.llm.dto.ChatToolMetadata toolMetadata, String sessionId) {
            return chat(message, model, toolMetadata, sessionId, null);
        }

        @Override
        public ChatResponse chat(
                String message,
                String model,
                net.jrodolfo.llm.dto.ChatToolMetadata toolMetadata,
                String sessionId,
                net.jrodolfo.llm.dto.PendingToolCallResponse pendingTool
        ) {
            this.lastPrompt = message;
            this.generateCalled = true;
            return new ChatResponse("plain response", resolveModel(model), toolMetadata, sessionId, pendingTool);
        }
    }

    private static class FakeMcpService extends McpService {
        private S3CloudwatchReportToolRequest lastS3Request;
        private ListReportsRequest lastListReportsRequest;
        private ReadReportSummaryToolRequest lastReadReportSummaryRequest;

        private FakeMcpService() {
            super(new FakeMcpClient(), new McpProperties(true, "node", List.of(), ".", 5, 30));
        }

        @Override
        public McpToolInvocationResponse runAwsRegionAudit(AwsRegionAuditToolRequest request) {
            return new McpToolInvocationResponse("aws_region_audit", Map.of(
                    "ok", true,
                    "summary", Map.of(
                            "success_count", 1,
                            "failure_count", 0,
                            "skipped_count", 19
                    )
            ));
        }

        @Override
        public McpToolInvocationResponse runS3CloudwatchReport(S3CloudwatchReportToolRequest request) {
            this.lastS3Request = request;
            return new McpToolInvocationResponse("s3_cloudwatch_report", Map.of(
                    "ok", true,
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

    private static final class FakeMcpClient extends McpClient {
        private FakeMcpClient() {
            super(new ObjectMapper(), new McpProperties(true, "node", List.of(), ".", 5, 30));
        }
    }
}
