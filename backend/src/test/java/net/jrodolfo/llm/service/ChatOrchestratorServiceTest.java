package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.McpClient;
import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.config.McpProperties;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.McpToolInvocationResponse;
import net.jrodolfo.llm.dto.ReadReportSummaryToolRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOrchestratorServiceTest {

    @Test
    void noToolRequestFallsBackToRegularChat() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        ChatOrchestratorService orchestrator = new ChatOrchestratorService(
                ollamaService,
                new FakeMcpService(),
                new ChatToolRouterService()
        );

        ChatResponse response = orchestrator.chat("explain recursion", "llama3:8b");

        assertEquals("plain response", response.response());
        assertEquals("explain recursion", ollamaService.lastPrompt);
        assertEquals(null, response.tool());
    }

    @Test
    void auditRequestUsesToolAndAddsMetadata() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        ChatOrchestratorService orchestrator = new ChatOrchestratorService(
                ollamaService,
                new FakeMcpService(),
                new ChatToolRouterService()
        );

        ChatResponse response = orchestrator.chat("run aws audit for us-east-2 sts", "llama3:8b");

        assertNotNull(response.tool());
        assertTrue(response.tool().used());
        assertEquals("aws_region_audit", response.tool().name());
        assertTrue(ollamaService.lastPrompt.contains("Tool name:\naws_region_audit"));
    }

    @Test
    void toolFailureReturnsExplicitFailureResponse() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        ChatOrchestratorService orchestrator = new ChatOrchestratorService(
                ollamaService,
                new ErrorMcpService(),
                new ChatToolRouterService()
        );

        ChatResponse response = orchestrator.chat("run aws audit for us-east-2 sts", "llama3:8b");

        assertTrue(response.response().contains("I tried to use the local tool"));
        assertNotNull(response.tool());
        assertEquals("failed", response.tool().status());
        assertFalse(ollamaService.generateCalled);
    }

    @Test
    void clarificationRequestReturnsImmediateClarificationResponse() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        ChatOrchestratorService orchestrator = new ChatOrchestratorService(
                ollamaService,
                new FakeMcpService(),
                new ChatToolRouterService()
        );

        ChatResponse response = orchestrator.chat("check bucket metrics for the last 7 days", "llama3:8b");

        assertTrue(response.response().contains("I can run the S3 CloudWatch report, but I need the bucket name."));
        assertNotNull(response.tool());
        assertEquals("clarification-needed", response.tool().status());
        assertFalse(ollamaService.generateCalled);
    }

    @Test
    void ambiguousLatestReportRequestReturnsClarificationResponse() {
        FakeOllamaService ollamaService = new FakeOllamaService();
        ChatOrchestratorService orchestrator = new ChatOrchestratorService(
                ollamaService,
                new FakeMcpService(),
                new ChatToolRouterService()
        );

        ChatResponse response = orchestrator.chat("read the latest report", "llama3:8b");

        assertTrue(response.response().contains("latest audit report or the latest s3 cloudwatch report"));
        assertNotNull(response.tool());
        assertEquals("clarification-needed", response.tool().status());
        assertFalse(ollamaService.generateCalled);
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
        public ChatResponse chat(String message, String model, net.jrodolfo.llm.dto.ChatToolMetadata toolMetadata) {
            this.lastPrompt = message;
            this.generateCalled = true;
            return new ChatResponse("plain response", resolveModel(model), toolMetadata);
        }
    }

    private static class FakeMcpService extends McpService {
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
            return new McpToolInvocationResponse("list_recent_reports", Map.of(
                    "ok", true,
                    "reports", List.of(Map.of("run_dir", "/tmp/report-1"))
            ));
        }

        @Override
        public McpToolInvocationResponse readReportSummary(ReadReportSummaryToolRequest request) {
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
