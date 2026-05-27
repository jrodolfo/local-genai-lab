package net.jrodolfo.llm.rag.controller;

import jakarta.validation.Valid;
import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.dto.RagIndexResponse;
import net.jrodolfo.llm.rag.dto.RagQueryRequest;
import net.jrodolfo.llm.rag.dto.RagQueryResponse;
import net.jrodolfo.llm.rag.dto.RagStatusResponse;
import net.jrodolfo.llm.rag.service.RagAnswerService;
import net.jrodolfo.llm.rag.service.RagCorpusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@Validated
public class RagController {

    private final RagProperties ragProperties;
    private final RagCorpusService ragCorpusService;
    private final RagAnswerService ragAnswerService;

    public RagController(
            RagProperties ragProperties,
            RagCorpusService ragCorpusService,
            RagAnswerService ragAnswerService
    ) {
        this.ragProperties = ragProperties;
        this.ragCorpusService = ragCorpusService;
        this.ragAnswerService = ragAnswerService;
    }

    @GetMapping("/status")
    public RagStatusResponse status() {
        RagCorpusService.CorpusSnapshot snapshot = ragCorpusService.snapshot();
        return new RagStatusResponse(
                ragProperties.enabled(),
                snapshot != null,
                ragProperties.resolvedCorpusRoot().toString(),
                snapshot != null ? snapshot.documents().size() : 0,
                snapshot != null ? snapshot.chunks().size() : 0,
                ragProperties.retrievalMode()
        );
    }

    @PostMapping("/index")
    public ResponseEntity<RagIndexResponse> index() {
        ensureEnabled();
        RagCorpusService.CorpusSnapshot snapshot = ragCorpusService.rebuildIndex();
        return ResponseEntity.ok(new RagIndexResponse(
                snapshot.corpusRoot().toString(),
                snapshot.documents().size(),
                snapshot.chunks().size(),
                ragProperties.retrievalMode()
        ));
    }

    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(@Valid @RequestBody RagQueryRequest request) {
        ensureEnabled();
        return ResponseEntity.ok(ragAnswerService.answer(
                request.question(),
                request.provider(),
                request.model(),
                request.sessionId()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        HttpStatus status = "RAG is disabled.".equals(ex.getMessage()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("error", ex.getMessage()));
    }

    private void ensureEnabled() {
        if (!ragProperties.enabled()) {
            throw new IllegalStateException("RAG is disabled.");
        }
    }
}
