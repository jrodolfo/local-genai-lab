package net.jrodolfo.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.jrodolfo.llm.dto.ChatSessionDetailResponse;
import net.jrodolfo.llm.dto.ChatSessionImportResponse;
import net.jrodolfo.llm.dto.ChatSessionSummaryResponse;
import net.jrodolfo.llm.service.ChatSessionExportService;
import net.jrodolfo.llm.service.ChatSessionImportException;
import net.jrodolfo.llm.service.ChatSessionImportService;
import net.jrodolfo.llm.service.ChatSessionNotFoundException;
import net.jrodolfo.llm.service.ChatSessionService;
import net.jrodolfo.llm.service.InvalidSessionIdException;
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
@Tag(name = "sessions", description = "Local JSON-backed conversation sessions, import/export, and filtering.")
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
    @Operation(summary = "List stored chat sessions", description = "Returns local session summaries filtered by optional text query, provider, tool usage, and pending clarification state.")
    public List<ChatSessionSummaryResponse> listSessions(
            @Parameter(description = "Text query matched against session title, summary, and message content.")
            @RequestParam(required = false) String query,
            @Parameter(description = "Legacy alias for `query`. Frontend uses `query`.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Optional provider filter.", example = "bedrock")
            @RequestParam(required = false) String provider,
            @Parameter(description = "Optional tool usage filter: `used` or `unused`.", example = "used")
            @RequestParam(required = false) String toolUsage,
            @Parameter(description = "Optional pending clarification filter.", example = "true")
            @RequestParam(required = false) Boolean pending
    ) {
        return chatSessionService.listSessions(
                (query == null || query.isBlank()) ? q : query,
                provider,
                toolUsage,
                pending
        );
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get a stored chat session", description = "Loads one saved session with full messages, tool data, provider metadata, and pending clarification state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session found."),
            @ApiResponse(responseCode = "404", description = "Session not found.")
    })
    public ChatSessionDetailResponse getSession(@PathVariable String sessionId) {
        return chatSessionService.getSession(sessionId);
    }

    @GetMapping("/{sessionId}/export")
    @Operation(summary = "Export a stored chat session", description = "Returns the saved session as JSON by default, or Markdown when `format=markdown` or `format=md`.")
    public ResponseEntity<?> exportSession(
            @PathVariable String sessionId,
            @Parameter(description = "Export format: `json` (default), `markdown`, or `md`.")
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
    @Operation(summary = "Delete a stored chat session")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session deleted."),
            @ApiResponse(responseCode = "404", description = "Session not found.")
    })
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        chatSessionService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @Operation(summary = "Import a JSON chat session export", description = "Accepts a JSON session export produced by this app. If the imported `sessionId` already exists locally, a new identifier is generated instead of overwriting.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session imported."),
            @ApiResponse(responseCode = "400", description = "Invalid import payload.")
    })
    public ChatSessionImportResponse importSession(@RequestParam("file") MultipartFile file) {
        return chatSessionImportService.importSession(file);
    }

    @ExceptionHandler(ChatSessionNotFoundException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleChatSessionNotFound(ChatSessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ChatSessionImportException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleChatSessionImport(ChatSessionImportException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InvalidSessionIdException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleInvalidSessionId(InvalidSessionIdException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
