package net.jrodolfo.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.model.PendingToolCall;
import net.jrodolfo.llm.service.ChatToolRouterService;
import net.jrodolfo.llm.service.ChatSessionExportService;
import net.jrodolfo.llm.service.ChatSessionImportService;
import net.jrodolfo.llm.service.ChatSessionMetadataService;
import net.jrodolfo.llm.service.ChatSessionService;
import net.jrodolfo.llm.service.FileChatSessionStore;
import net.jrodolfo.llm.service.SessionIdPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private FileChatSessionStore sessionStore;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        SessionIdPolicy sessionIdPolicy = new SessionIdPolicy();
        sessionStore = new FileChatSessionStore(objectMapper, new AppStorageProperties(tempDir.resolve("sessions").toString(), tempDir.resolve("reports").toString()), sessionIdPolicy);
        ChatSessionMetadataService metadataService = new ChatSessionMetadataService();
        ChatSessionService sessionService = new ChatSessionService(sessionStore, metadataService);
        ChatSessionExportService exportService = new ChatSessionExportService();
        ChatSessionImportService importService = new ChatSessionImportService(objectMapper, sessionStore, metadataService, sessionIdPolicy);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new SessionController(sessionService, exportService, importService))
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new StringHttpMessageConverter(),
                        new ByteArrayHttpMessageConverter()
                )
                .build();
    }

    @Test
    void listSessionsReturnsMostRecentFirst() throws Exception {
        saveSession("older-session", "older question", Instant.parse("2026-04-09T10:00:00Z"));
        saveSession("newer-session", "newer question", Instant.parse("2026-04-10T10:00:00Z"));

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("newer-session"))
                .andExpect(jsonPath("$[0].title").value("newer question"))
                .andExpect(jsonPath("$[0].summary").value("done"))
                .andExpect(jsonPath("$[1].sessionId").value("older-session"));
    }

    @Test
    void listSessionsFiltersByQueryAcrossSessionContent() throws Exception {
        saveSession("session-1", "bedrock latency question", Instant.parse("2026-04-10T10:00:00Z"));
        saveSession("session-2", "run aws audit", Instant.parse("2026-04-11T10:00:00Z"));

        mockMvc.perform(get("/api/sessions").queryParam("query", "bedrock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void listSessionsStillAcceptsLegacyQParameter() throws Exception {
        saveSession("session-1", "bedrock latency question", Instant.parse("2026-04-10T10:00:00Z"));
        saveSession("session-2", "run aws audit", Instant.parse("2026-04-11T10:00:00Z"));

        mockMvc.perform(get("/api/sessions").queryParam("q", "bedrock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("session-1"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void listSessionsFiltersByProvider() throws Exception {
        saveSession("bedrock-session", "bedrock question", Instant.parse("2026-04-10T10:00:00Z"));
        saveSession("ollama-session", "ollama question", Instant.parse("2026-04-11T10:00:00Z"), null, "ollama", false);

        mockMvc.perform(get("/api/sessions").queryParam("provider", "bedrock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("bedrock-session"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void listSessionsFiltersByToolUsage() throws Exception {
        saveSession("used-tool-session", "run aws audit", Instant.parse("2026-04-10T10:00:00Z"));
        saveSession("unused-tool-session", "plain chat", Instant.parse("2026-04-11T10:00:00Z"), null, "ollama", true);

        mockMvc.perform(get("/api/sessions").queryParam("toolUsage", "unused"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("unused-tool-session"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void listSessionsFiltersByPendingState() throws Exception {
        saveSession("pending-session", "check bucket metrics", Instant.parse("2026-04-10T10:00:00Z"), new PendingToolCall(
                ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT,
                null,
                null,
                null,
                7,
                "s3 cloudwatch metrics request",
                List.of(),
                List.of("bucket")
        ));
        saveSession("complete-session", "run aws audit", Instant.parse("2026-04-11T10:00:00Z"));

        mockMvc.perform(get("/api/sessions").queryParam("pending", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("pending-session"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void listSessionsCombinesFiltersWithAndSemantics() throws Exception {
        saveSession("bedrock-pending", "check bucket metrics", Instant.parse("2026-04-10T10:00:00Z"), new PendingToolCall(
                ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT,
                null,
                null,
                null,
                7,
                "s3 cloudwatch metrics request",
                List.of(),
                List.of("bucket")
        ));
        saveSession("bedrock-complete", "check bucket metrics", Instant.parse("2026-04-11T10:00:00Z"));
        saveSession("ollama-pending", "check bucket metrics", Instant.parse("2026-04-12T10:00:00Z"), new PendingToolCall(
                ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT,
                null,
                null,
                null,
                7,
                "s3 cloudwatch metrics request",
                List.of(),
                List.of("bucket")
        ), "ollama", false);

        mockMvc.perform(get("/api/sessions")
                        .queryParam("query", "bucket")
                        .queryParam("provider", "bedrock")
                        .queryParam("toolUsage", "used")
                        .queryParam("pending", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("bedrock-pending"))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void getSessionReturnsStoredMessages() throws Exception {
        saveSession("session-1", "run aws audit", Instant.parse("2026-04-10T10:00:00Z"));

        mockMvc.perform(get("/api/sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.summary").value("done"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1].tool.name").value("aws_region_audit"))
                .andExpect(jsonPath("$.messages[1].metadata.provider").value("bedrock"));
    }

    @Test
    void getSessionIncludesPendingToolWhenPresent() throws Exception {
        saveSession(
                "session-1",
                "check bucket metrics",
                Instant.parse("2026-04-10T10:00:00Z"),
                new PendingToolCall(
                        ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT,
                        null,
                        null,
                        null,
                        7,
                        "s3 cloudwatch metrics request",
                        List.of(),
                        List.of("bucket")
                )
        );

        mockMvc.perform(get("/api/sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingTool.toolName").value("s3_cloudwatch_report"))
                .andExpect(jsonPath("$.pendingTool.missingFields[0]").value("bucket"));
    }

    @Test
    void exportSessionReturnsJsonAttachment() throws Exception {
        saveSession("session-1", "run aws audit", Instant.parse("2026-04-10T10:00:00Z"));

        mockMvc.perform(get("/api/sessions/session-1/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.messages[1].metadata.provider").value("bedrock"))
                .andExpect(jsonPath("$.messages[1].tool.name").value("aws_region_audit"))
                .andExpect(jsonPath("$.summary").value("done"))
                .andExpect(jsonPath("$.pendingTool").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "attachment; filename=\"session-1.json\""));
    }

    @Test
    void exportSessionReturnsMarkdownAttachment() throws Exception {
        saveSession(
                "session-1",
                "check bucket metrics",
                Instant.parse("2026-04-10T10:00:00Z"),
                new PendingToolCall(
                        ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT,
                        null,
                        null,
                        null,
                        7,
                        "s3 cloudwatch metrics request",
                        List.of(),
                        List.of("bucket")
                )
        );

        mockMvc.perform(get("/api/sessions/session-1/export").queryParam("format", "markdown"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType("text/markdown"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("# check bucket metrics")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("## pending tool")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("awaiting tool: s3_cloudwatch_report")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("provider: bedrock")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "attachment; filename=\"session-1.md\""));
    }

    @Test
    void importSessionStoresJsonExport() throws Exception {
        String json = """
                {
                  "sessionId": "imported-session",
                  "title": "imported title",
                  "summary": "imported summary",
                  "model": "llama3:8b",
                  "createdAt": "2026-04-10T10:00:00Z",
                  "updatedAt": "2026-04-10T10:01:00Z",
                  "pendingTool": null,
                  "messages": [
                    {
                      "role": "user",
                      "content": "run aws audit",
                      "tool": null,
                      "metadata": null,
                      "timestamp": "2026-04-10T10:00:00Z"
                    },
                    {
                      "role": "assistant",
                      "content": "done",
                      "tool": {
                        "used": true,
                        "name": "aws_region_audit",
                        "status": "success",
                        "summary": "done"
                      },
                      "metadata": {
                        "provider": "bedrock",
                        "modelId": "amazon.nova-lite-v1:0",
                        "stopReason": "end_turn",
                        "inputTokens": 1,
                        "outputTokens": 2,
                        "totalTokens": 3,
                        "durationMs": 100,
                        "providerLatencyMs": 90
                      },
                      "timestamp": "2026-04-10T10:01:00Z"
                    }
                  ]
                }
                """;
        MockMultipartFile file = new MockMultipartFile("file", "session.json", "application/json", json.getBytes());

        mockMvc.perform(multipart("/api/sessions/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("imported-session"))
                .andExpect(jsonPath("$.title").value("imported title"))
                .andExpect(jsonPath("$.idChanged").value(false))
                .andExpect(jsonPath("$.messageCount").value(2));

        mockMvc.perform(get("/api/sessions/imported-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[1].metadata.provider").value("bedrock"));
    }

    @Test
    void mixedProviderMetadataSurvivesExportImportRoundTrip() throws Exception {
        ChatSession session = new ChatSession(
                "mixed-session",
                "llama3:8b",
                Instant.parse("2026-04-10T10:00:00Z"),
                Instant.parse("2026-04-10T10:03:00Z"),
                List.of(
                        new net.jrodolfo.llm.model.ChatSessionMessage("user", "Explain recursion.", null, null, null, Instant.parse("2026-04-10T10:00:00Z")),
                        new net.jrodolfo.llm.model.ChatSessionMessage(
                                "assistant",
                                "Recursion is when a function calls itself.",
                                null,
                                null,
                                new ModelProviderMetadata("ollama", "llama3:8b", "stop", 10, 20, 30, 100L, 90L, 110L, 120L),
                                Instant.parse("2026-04-10T10:01:00Z")
                        ),
                        new net.jrodolfo.llm.model.ChatSessionMessage("user", "Now answer with Bedrock.", null, null, null, Instant.parse("2026-04-10T10:02:00Z")),
                        new net.jrodolfo.llm.model.ChatSessionMessage(
                                "assistant",
                                "Bedrock can answer in the same session too.",
                                null,
                                null,
                                new ModelProviderMetadata("bedrock", "us.amazon.nova-pro-v1:0", "end_turn", 11, 21, 32, 200L, 180L, 210L, 220L),
                                Instant.parse("2026-04-10T10:03:00Z")
                        )
                ),
                null,
                "mixed provider session",
                "compares ollama and bedrock"
        );
        sessionStore.save(session);

        String exportedJson = mockMvc.perform(get("/api/sessions/mixed-session/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[1].metadata.provider").value("ollama"))
                .andExpect(jsonPath("$.messages[1].metadata.modelId").value("llama3:8b"))
                .andExpect(jsonPath("$.messages[3].metadata.provider").value("bedrock"))
                .andExpect(jsonPath("$.messages[3].metadata.modelId").value("us.amazon.nova-pro-v1:0"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String importedJson = exportedJson.replace("\"sessionId\":\"mixed-session\"", "\"sessionId\":\"mixed-session-imported\"");
        MockMultipartFile file = new MockMultipartFile("file", "mixed-session.json", "application/json", importedJson.getBytes());

        mockMvc.perform(multipart("/api/sessions/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("mixed-session-imported"))
                .andExpect(jsonPath("$.idChanged").value(false));

        mockMvc.perform(get("/api/sessions/mixed-session-imported"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[1].metadata.provider").value("ollama"))
                .andExpect(jsonPath("$.messages[1].metadata.modelId").value("llama3:8b"))
                .andExpect(jsonPath("$.messages[3].metadata.provider").value("bedrock"))
                .andExpect(jsonPath("$.messages[3].metadata.modelId").value("us.amazon.nova-pro-v1:0"));
    }

    @Test
    void importSessionGeneratesNewIdOnCollision() throws Exception {
        saveSession("session-1", "existing session", Instant.parse("2026-04-10T10:00:00Z"));
        String json = """
                {
                  "sessionId": "session-1",
                  "title": "imported title",
                  "summary": "imported summary",
                  "model": "llama3:8b",
                  "messages": [
                    {
                      "role": "user",
                      "content": "new content",
                      "tool": null,
                      "metadata": null,
                      "timestamp": "2026-04-10T10:02:00Z"
                    }
                  ]
                }
                """;
        MockMultipartFile file = new MockMultipartFile("file", "collision.json", "application/json", json.getBytes());

        mockMvc.perform(multipart("/api/sessions/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.sessionId").value(org.hamcrest.Matchers.not("session-1")))
                .andExpect(jsonPath("$.idChanged").value(true));
    }

    @Test
    void importSessionRejectsInvalidJson() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "invalid.json", "application/json", "{".getBytes());

        mockMvc.perform(multipart("/api/sessions/import").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Import file is not valid JSON."));
    }

    @Test
    void deleteSessionRemovesFile() throws Exception {
        saveSession("session-1", "run aws audit", Instant.parse("2026-04-10T10:00:00Z"));

        mockMvc.perform(delete("/api/sessions/session-1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/sessions/session-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownSessionReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/sessions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Chat session not found: missing"));
    }

    @Test
    void getSessionRejectsInvalidSessionId() throws Exception {
        mockMvc.perform(get("/api/sessions/bad%2Fid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid session id."));
    }

    @Test
    void exportSessionRejectsInvalidSessionId() throws Exception {
        mockMvc.perform(get("/api/sessions/bad%2Fid/export"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid session id."));
    }

    @Test
    void deleteSessionRejectsInvalidSessionId() throws Exception {
        mockMvc.perform(delete("/api/sessions/bad%2Fid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid session id."));
    }

    @Test
    void importSessionReplacesUnsafeImportedSessionId() throws Exception {
        String json = """
                {
                  "sessionId": "../secret",
                  "title": "imported title",
                  "summary": "imported summary",
                  "model": "llama3:8b",
                  "messages": [
                    {
                      "role": "user",
                      "content": "new content",
                      "tool": null,
                      "metadata": null,
                      "timestamp": "2026-04-10T10:02:00Z"
                    }
                  ]
                }
                """;
        MockMultipartFile file = new MockMultipartFile("file", "unsafe.json", "application/json", json.getBytes());

        mockMvc.perform(multipart("/api/sessions/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.sessionId").value(org.hamcrest.Matchers.not("../secret")))
                .andExpect(jsonPath("$.idChanged").value(true));
    }

    private void saveSession(String sessionId, String userMessage, Instant timestamp) {
        saveSession(sessionId, userMessage, timestamp, null);
    }

    private void saveSession(String sessionId, String userMessage, Instant timestamp, PendingToolCall pendingToolCall) {
        saveSession(sessionId, userMessage, timestamp, pendingToolCall, "bedrock", false);
    }

    private void saveSession(String sessionId, String userMessage, Instant timestamp, PendingToolCall pendingToolCall, String provider, boolean noTool) {
        ChatSession session = new ChatSession(
                sessionId,
                "llama3:8b",
                timestamp,
                timestamp.plusSeconds(30),
                List.of(
                        new net.jrodolfo.llm.model.ChatSessionMessage("user", userMessage, null, null, null, timestamp),
                        new net.jrodolfo.llm.model.ChatSessionMessage(
                                "assistant",
                                "done",
                                noTool ? null : new ChatToolMetadata(true, "aws_region_audit", "success", "done"),
                                null,
                                new ModelProviderMetadata(
                                        provider,
                                        "bedrock".equals(provider) ? "amazon.nova-lite-v1:0" : "llama3:8b",
                                        "end_turn",
                                        1,
                                        2,
                                        3,
                                        100L,
                                        90L,
                                        110L,
                                        120L
                                ),
                                timestamp.plusSeconds(30)
                        )
                ),
                pendingToolCall,
                null,
                null
        );
        sessionStore.save(session);
    }
}
