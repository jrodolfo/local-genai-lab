package net.jrodolfo.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.service.ChatArtifactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArtifactControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private Path runDir;

    @BeforeEach
    void setUp() throws Exception {
        Path reportsDir = tempDir.resolve("reports");
        runDir = reportsDir.resolve("audit/aws-audit-2026-04-12_15-00-00");
        Files.createDirectories(runDir);
        Files.writeString(runDir.resolve("report.txt"), "audit report\nall good\n");
        Files.writeString(runDir.resolve("summary.json"), "{\"success_count\":10,\"failure_count\":0}");
        Files.writeString(runDir.resolve("stderr.log"), "stderr details");

        ChatArtifactService chatArtifactService = new ChatArtifactService(
                new AppStorageProperties(tempDir.resolve("sessions").toString(), reportsDir.toString())
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ArtifactController(chatArtifactService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void listFilesReturnsFilesUnderRunDirectory() throws Exception {
        mockMvc.perform(get("/api/artifacts/files").queryParam("runDir", "audit/aws-audit-2026-04-12_15-00-00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[?(@.name == 'report.txt')]").isArray())
                .andExpect(jsonPath("$[?(@.name == 'summary.json')]").isArray())
                .andExpect(jsonPath("$[?(@.path == 'audit/aws-audit-2026-04-12_15-00-00/report.txt')]").isArray());
    }

    @Test
    void previewReturnsArtifactContent() throws Exception {
        mockMvc.perform(get("/api/artifacts/preview").queryParam("path", "audit/aws-audit-2026-04-12_15-00-00/report.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("audit/aws-audit-2026-04-12_15-00-00/report.txt"))
                .andExpect(jsonPath("$.fileName").value("report.txt"))
                .andExpect(jsonPath("$.contentType").value("text/plain"))
                .andExpect(jsonPath("$.content").value(containsString("audit report")));
    }

    @Test
    void previewRejectsAbsolutePaths() throws Exception {
        mockMvc.perform(get("/api/artifacts/preview").queryParam("path", runDir.resolve("report.txt").toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("must be relative")));
    }

    @Test
    void previewRejectsPathTraversalOutsideReportsDirectory() throws Exception {
        mockMvc.perform(get("/api/artifacts/preview").queryParam("path", "../outside.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("outside the allowed reports directory")));
    }
}
