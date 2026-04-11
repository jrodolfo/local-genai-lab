package net.jrodolfo.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.service.ChatSessionService;
import net.jrodolfo.llm.service.FileChatSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        sessionStore = new FileChatSessionStore(objectMapper, new AppStorageProperties(tempDir.resolve("sessions").toString()));
        ChatSessionService sessionService = new ChatSessionService(sessionStore);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new SessionController(sessionService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
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
                .andExpect(jsonPath("$[1].sessionId").value("older-session"));
    }

    @Test
    void getSessionReturnsStoredMessages() throws Exception {
        saveSession("session-1", "run aws audit", Instant.parse("2026-04-10T10:00:00Z"));

        mockMvc.perform(get("/api/sessions/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1].tool.name").value("aws_region_audit"));
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

    private void saveSession(String sessionId, String userMessage, Instant timestamp) {
        ChatSession session = new ChatSession(
                sessionId,
                "llama3:8b",
                timestamp,
                timestamp.plusSeconds(30),
                List.of(
                        new net.jrodolfo.llm.model.ChatSessionMessage("user", userMessage, null, timestamp),
                        new net.jrodolfo.llm.model.ChatSessionMessage(
                                "assistant",
                                "done",
                                new ChatToolMetadata(true, "aws_region_audit", "success", "done"),
                                timestamp.plusSeconds(30)
                        )
                )
        );
        sessionStore.save(session);
    }
}
