package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.McpClient;
import net.jrodolfo.llm.config.McpProperties;
import net.jrodolfo.llm.dto.AwsRegionAuditToolRequest;
import net.jrodolfo.llm.dto.ListReportsRequest;
import net.jrodolfo.llm.dto.S3CloudwatchReportToolRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServiceTest {

    @Test
    void listToolsReturnsDisabledResponseWhenMcpIsOff() {
        FakeMcpClient fakeClient = new FakeMcpClient();
        McpService service = new McpService(fakeClient, properties(false));

        var response = service.listTools();

        assertFalse(response.enabled());
        assertTrue(response.tools().isEmpty());
        assertFalse(fakeClient.listToolsCalled);
    }

    @Test
    void runAwsRegionAuditPassesOnlyProvidedArguments() {
        FakeMcpClient fakeClient = new FakeMcpClient();
        fakeClient.nextCallToolResult = Map.of("ok", true);
        McpService service = new McpService(fakeClient, properties(true));

        var response = service.runAwsRegionAudit(new AwsRegionAuditToolRequest(
                List.of("us-east-2"),
                List.of("sts", "s3")
        ));

        assertEquals("aws_region_audit", response.tool());
        assertEquals("aws_region_audit", fakeClient.lastToolName);
        assertEquals(List.of("us-east-2"), fakeClient.lastArguments.get("regions"));
        assertEquals(List.of("sts", "s3"), fakeClient.lastArguments.get("services"));
    }

    @Test
    void runS3CloudwatchReportTrimsStringInputs() {
        FakeMcpClient fakeClient = new FakeMcpClient();
        fakeClient.nextCallToolResult = Map.of("ok", true);
        McpService service = new McpService(fakeClient, properties(true));

        service.runS3CloudwatchReport(new S3CloudwatchReportToolRequest("  jrodolfo.net  ", "  us-east-2  ", 7));

        assertEquals("s3_cloudwatch_report", fakeClient.lastToolName);
        assertEquals("jrodolfo.net", fakeClient.lastArguments.get("bucket"));
        assertEquals("us-east-2", fakeClient.lastArguments.get("region"));
        assertEquals(7, fakeClient.lastArguments.get("days"));
    }

    @Test
    void listRecentReportsUsesDefaultValuesWhenInputIsBlank() {
        FakeMcpClient fakeClient = new FakeMcpClient();
        fakeClient.nextCallToolResult = Map.of("ok", true);
        McpService service = new McpService(fakeClient, properties(true));

        service.listRecentReports(new ListReportsRequest(null, null));

        assertEquals("list_recent_reports", fakeClient.lastToolName);
        assertEquals("all", fakeClient.lastArguments.get("report_type"));
        assertEquals(10, fakeClient.lastArguments.get("limit"));
    }

    @Test
    void listRecentReportsRejectsUnsupportedReportType() {
        FakeMcpClient fakeClient = new FakeMcpClient();
        McpService service = new McpService(fakeClient, properties(true));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.listRecentReports(new ListReportsRequest("invalid", 5))
        );

        assertEquals("Unsupported reportType: invalid", error.getMessage());
    }

    private static McpProperties properties(boolean enabled) {
        return new McpProperties(enabled, "node", List.of(), ".", 5, 30);
    }

    private static final class FakeMcpClient extends McpClient {
        private boolean listToolsCalled;
        private String lastToolName;
        private Map<String, Object> lastArguments;
        private Map<String, Object> nextCallToolResult = Map.of();

        private FakeMcpClient() {
            super(new ObjectMapper(), properties(true));
        }

        @Override
        public List<McpToolDescriptor> listTools() {
            listToolsCalled = true;
            return List.of(new McpToolDescriptor("list_recent_reports", "List Recent Reports", "desc", Map.of()));
        }

        @Override
        public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
            this.lastToolName = toolName;
            this.lastArguments = arguments;
            return nextCallToolResult;
        }
    }
}
