package net.jrodolfo.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import net.jrodolfo.llm.dto.ArtifactFileResponse;
import net.jrodolfo.llm.dto.ArtifactPreviewResponse;
import net.jrodolfo.llm.service.ArtifactAccessException;
import net.jrodolfo.llm.service.ChatArtifactService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/artifacts")
@Tag(name = "artifacts", description = "Read-only preview and file listing for local report artifacts under the configured reports directory.")
public class ArtifactController {

    private final ChatArtifactService chatArtifactService;

    public ArtifactController(ChatArtifactService chatArtifactService) {
        this.chatArtifactService = chatArtifactService;
    }

    @GetMapping("/files")
    @Operation(summary = "List files for a report run directory", description = "Returns files under a run directory rooted in the configured reports directory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Artifact files listed successfully."),
            @ApiResponse(responseCode = "400", description = "Path is outside the allowed reports directory or not a directory."),
            @ApiResponse(responseCode = "404", description = "Artifact directory not found.")
    })
    public List<ArtifactFileResponse> listFiles(
            @Parameter(description = "Run directory path relative to the configured reports directory.", required = true)
            @RequestParam String runDir
    ) {
        return chatArtifactService.listFiles(runDir);
    }

    @GetMapping("/preview")
    @Operation(summary = "Preview a text or JSON artifact", description = "Returns a safe read-only preview for supported text-based artifact files under the configured reports directory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Artifact preview returned successfully."),
            @ApiResponse(responseCode = "400", description = "Path is outside the allowed reports directory, not a file, or not previewable."),
            @ApiResponse(responseCode = "404", description = "Artifact file not found.")
    })
    public ArtifactPreviewResponse preview(
            @Parameter(description = "Artifact file path relative to the configured reports directory.", required = true)
            @RequestParam String path
    ) {
        return chatArtifactService.previewFile(path);
    }

    @ExceptionHandler(ArtifactAccessException.class)
    @Operation(hidden = true)
    public ResponseEntity<Map<String, String>> handleArtifactAccess(ArtifactAccessException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("error", ex.getMessage()));
    }
}
