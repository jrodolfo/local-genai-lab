package net.jrodolfo.llm.controller;

import net.jrodolfo.llm.dto.ChatSessionDetailResponse;
import net.jrodolfo.llm.dto.ChatSessionImportResponse;
import net.jrodolfo.llm.dto.ChatSessionSummaryResponse;
import net.jrodolfo.llm.service.ChatSessionExportService;
import net.jrodolfo.llm.service.ChatSessionImportException;
import net.jrodolfo.llm.service.ChatSessionImportService;
import net.jrodolfo.llm.service.ChatSessionNotFoundException;
import net.jrodolfo.llm.service.ChatSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ChatSessionService chatSessionService;
    private final ChatSessionExportService chatSessionExportService;
    private final ChatSessionImportService chatSessionImportService;

    public SessionController(
            ChatSessionService chatSessionService,
            ChatSessionExportService chatSessionExportService,
            ChatSessionImportService chatSessionImportService
    ) {
        this.chatSessionService = chatSessionService;
        this.chatSessionExportService = chatSessionExportService;
        this.chatSessionImportService = chatSessionImportService;
    }

    @GetMapping
    public List<ChatSessionSummaryResponse> listSessions(@RequestParam(required = false) String q) {
        return chatSessionService.listSessions(q);
    }

    @GetMapping("/{sessionId}")
    public ChatSessionDetailResponse getSession(@PathVariable String sessionId) {
        return chatSessionService.getSession(sessionId);
    }

    @GetMapping("/{sessionId}/export")
    public ResponseEntity<?> exportSession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "json") String format
    ) {
        ChatSessionDetailResponse session = chatSessionService.getSession(sessionId);
        if ("markdown".equalsIgnoreCase(format) || "md".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/markdown"))
                    .header("Content-Disposition", "attachment; filename=\"" + session.sessionId() + ".md\"")
                    .body(chatSessionExportService.toMarkdown(session).getBytes(StandardCharsets.UTF_8));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=\"" + session.sessionId() + ".json\"")
                .body(session);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        chatSessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    public ChatSessionImportResponse importSession(@RequestParam("file") MultipartFile file) {
        return chatSessionImportService.importSession(file);
    }

    @ExceptionHandler(ChatSessionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleChatSessionNotFound(ChatSessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ChatSessionImportException.class)
    public ResponseEntity<Map<String, String>> handleChatSessionImport(ChatSessionImportException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
