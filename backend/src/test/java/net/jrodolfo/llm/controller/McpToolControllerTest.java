package net.jrodolfo.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.client.McpClient;
import net.jrodolfo.llm.client.McpClientException;
import net.jrodolfo.llm.config.McpProperties;
import net.jrodolfo.llm.dto.McpToolInvocationResponse;
import net.jrodolfo.llm.dto.McpToolListResponse;
import net.jrodolfo.llm.service.McpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpToolControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new McpToolController(new FakeMcpService()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setValidator(validator)
                .build();
    }

    @Test
    void listToolsReturnsConfiguredTools() throws Exception {
        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.tools[0].name").value("list_recent_reports"));
    }

    @Test
    void listReportsReturnsInvocationPayload() throws Exception {
        mockMvc.perform(get("/api/tools/reports").param("reportType", "all").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("list_recent_reports"))
                .andExpect(jsonPath("$.result.ok").value(true));
    }

    @Test
    void readReportSummaryRejectsBlankRunDir() throws Exception {
        mockMvc.perform(post("/api/tools/reports/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "runDir": "   ",
                                  "previewLines": 5
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mcpClientExceptionIsMappedToBadGateway() throws Exception {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new McpToolController(new ErrorThrowingMcpService()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();

        mockMvc.perform(post("/api/tools/aws-region-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "regions": ["us-east-2"],
                                  "services": ["sts"]
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("simulated mcp failure"));
    }

    private static final class FakeMcpService extends McpService {
        private FakeMcpService() {
            super(new FakeMcpClient(), new McpProperties(true, "node", List.of(), ".", 5, 30));
        }

        @Override
        public McpToolListResponse listTools() {
            return new McpToolListResponse(
                    true,
                    List.of(new McpToolListResponse.McpToolResponse(
                            "list_recent_reports",
                            "List Recent Reports",
                            "List reports",
                            Map.of("type", "object")
                    ))
            );
        }

        @Override
        public McpToolInvocationResponse listRecentReports(net.jrodolfo.llm.dto.ListReportsRequest request) {
            return new McpToolInvocationResponse("list_recent_reports", Map.of("ok", true));
        }

        @Override
        public McpToolInvocationResponse readReportSummary(net.jrodolfo.llm.dto.ReadReportSummaryToolRequest request) {
            return new McpToolInvocationResponse("read_report_summary", Map.of("ok", true));
        }
    }

    private static final class ErrorThrowingMcpService extends McpService {
        private ErrorThrowingMcpService() {
            super(new FakeMcpClient(), new McpProperties(true, "node", List.of(), ".", 5, 30));
        }

        @Override
        public McpToolInvocationResponse runAwsRegionAudit(net.jrodolfo.llm.dto.AwsRegionAuditToolRequest request) {
            throw new McpClientException("simulated mcp failure");
        }
    }

    private static final class FakeMcpClient extends McpClient {
        private FakeMcpClient() {
            super(new ObjectMapper(), new McpProperties(true, "node", List.of(), ".", 5, 30));
        }
    }
}
