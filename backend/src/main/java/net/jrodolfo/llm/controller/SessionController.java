package net.jrodolfo.llm.controller;

import net.jrodolfo.llm.dto.ChatSessionDetailResponse;
import net.jrodolfo.llm.dto.ChatSessionSummaryResponse;
import net.jrodolfo.llm.service.ChatSessionNotFoundException;
import net.jrodolfo.llm.service.ChatSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ChatSessionService chatSessionService;

    public SessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping
    public List<ChatSessionSummaryResponse> listSessions() {
        return chatSessionService.listSessions();
    }

    @GetMapping("/{sessionId}")
    public ChatSessionDetailResponse getSession(@PathVariable String sessionId) {
        return chatSessionService.getSession(sessionId);
    }

    @GetMapping("/{sessionId}/export")
    public ResponseEntity<ChatSessionDetailResponse> exportSession(@PathVariable String sessionId) {
        ChatSessionDetailResponse session = chatSessionService.getSession(sessionId);
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

    @ExceptionHandler(ChatSessionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleChatSessionNotFound(ChatSessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
