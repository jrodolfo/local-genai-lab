package net.jrodolfo.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * HTTP API for locally persisted conversation sessions.
 *
 * <p>Sessions are stored as JSON files by the service layer and can represent
 * normal chat or RAG conversations. The controller keeps the transport contract
 * small: list/filter sessions, load one session, delete one session, and move
 * sessions across machines through JSON or Markdown export/import.
 */
@RestController
@RequestMapping("/api/sessions")
@Tag(name = "sessions", description = "Local JSON-backed conversation sessions, import/export, and filtering.")
public class SessionController {

    private final ChatSessionService chatSessionService;
    private final ChatSessionExportService chatSessionExportService;
    private final ChatSessionImportService chatSessionImportService;

    /**
     * Constructs a new SessionController with the specified services.
     *
     * @param chatSessionService       the service for session management.
     * @param chatSessionExportService the service for exporting sessions.
     * @param chatSessionImportService the service for importing sessions.
     */
    public SessionController(
            ChatSessionService chatSessionService,
            ChatSessionExportService chatSessionExportService,
            ChatSessionImportService chatSessionImportService
    ) {
        this.chatSessionService = chatSessionService;
        this.chatSessionExportService = chatSessionExportService;
        this.chatSessionImportService = chatSessionImportService;
    }

    /**
     * Lists stored sessions with optional frontend filters.
     *
     * @param query     text query for title, summary, or message content
     * @param q         legacy alias for {@code query}
     * @param provider  optional provider filter, applied to provider metadata stored on messages
     * @param toolUsage optional tool usage filter: {@code used} or {@code unused}
     * @param pending   optional pending clarification filter
     * @param mode      optional session mode filter such as {@code chat} or {@code rag}
     * @return newest sessions first, reduced to summary DTOs
     */
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
            @RequestParam(required = false) Boolean pending,
            @Parameter(description = "Optional session mode filter.", example = "rag")
            @RequestParam(required = false) String mode
    ) {
        return chatSessionService.listSessions(
                (query == null || query.isBlank()) ? q : query,
                provider,
                toolUsage,
                pending,
                mode
        );
    }

    /**
     * Retrieves a specific chat session by its identifier.
     *
     * @param sessionId the unique identifier of the session.
     * @return the detailed session data.
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Get a stored chat session", description = "Loads one saved session with full messages, tool data, provider metadata, and pending clarification state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session found."),
            @ApiResponse(responseCode = "404", description = "Session not found.")
    })
    public ChatSessionDetailResponse getSession(@PathVariable String sessionId) {
        return chatSessionService.getSession(sessionId);
    }

    /**
     * Exports one session for backup, sharing, or reading.
     *
     * <p>JSON exports preserve the importable session structure. Markdown
     * exports are intended for human review and are not used for import.
     *
     * @param sessionId the unique identifier of the session
     * @param format    the export format: {@code json}, {@code markdown}, or {@code md}
     * @return the exported session with a download filename
     */
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
                .body(new ByteArrayResource(chatSessionExportService.toJson(session)));
    }

    /**
     * Deletes a specific chat session.
     *
     * @param sessionId the unique identifier of the session.
     * @return a 204 No Content response on success.
     */
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

    /**
     * Imports a JSON session export without overwriting an existing session.
     *
     * @param file multipart file containing a JSON export produced by this app
     * @return import result including the stored session id and whether it changed
     */
    @PostMapping("/import")
    @Operation(summary = "Import a JSON chat session export", description = "Accepts a JSON session export produced by this app. If the imported `sessionId` already exists locally, a new identifier is generated instead of overwriting.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session imported."),
            @ApiResponse(responseCode = "400", description = "Invalid import payload.")
    })
    public ChatSessionImportResponse importSession(@RequestParam("file") MultipartFile file) {
        return chatSessionImportService.importSession(file);
    }
}
