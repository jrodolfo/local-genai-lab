package net.jrodolfo.llm.controller;

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
public class ArtifactController {

    private final ChatArtifactService chatArtifactService;

    public ArtifactController(ChatArtifactService chatArtifactService) {
        this.chatArtifactService = chatArtifactService;
    }

    @GetMapping("/files")
    public List<ArtifactFileResponse> listFiles(@RequestParam String runDir) {
        return chatArtifactService.listFiles(runDir);
    }

    @GetMapping("/preview")
    public ArtifactPreviewResponse preview(@RequestParam String path) {
        return chatArtifactService.previewFile(path);
    }

    @ExceptionHandler(ArtifactAccessException.class)
    public ResponseEntity<Map<String, String>> handleArtifactAccess(ArtifactAccessException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("error", ex.getMessage()));
    }
}
