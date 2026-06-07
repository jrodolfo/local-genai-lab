package net.jrodolfo.llm.rag.controller;

import jakarta.validation.Valid;
import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.config.RagRetrievalMode;
import net.jrodolfo.llm.rag.dto.RagIndexRequest;
import net.jrodolfo.llm.rag.dto.RagIndexResponse;
import net.jrodolfo.llm.rag.dto.RagQueryRequest;
import net.jrodolfo.llm.rag.dto.RagQueryResponse;
import net.jrodolfo.llm.rag.dto.RagStatusResponse;
import net.jrodolfo.llm.rag.qdrant.QdrantStatus;
import net.jrodolfo.llm.rag.qdrant.QdrantStatusService;
import net.jrodolfo.llm.rag.service.RagAnswerService;
import net.jrodolfo.llm.rag.service.RagCorpusService;
import net.jrodolfo.llm.rag.service.RagRetrievalOptions;
import net.jrodolfo.llm.rag.service.RagVectorIndexingException;
import net.jrodolfo.llm.rag.service.RagVectorRetrievalException;
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

/**
 * REST controller for RAG (Retrieval-Augmented Generation) operations.
 * Provides endpoints for checking status, rebuilding the index, and querying the RAG system.
 */
@RestController
@RequestMapping("/api/rag")
@Validated
public class RagController {

    private final RagProperties ragProperties;
    private final RagCorpusService ragCorpusService;
    private final RagAnswerService ragAnswerService;
    private final QdrantStatusService qdrantStatusService;

    /**
     * Constructs a new RagController with the necessary services.
     *
     * @param ragProperties    The configuration properties for RAG.
     * @param ragCorpusService The service responsible for managing the document corpus.
     * @param ragAnswerService The service responsible for generating RAG-based answers.
     * @param qdrantStatusService The service responsible for checking optional Qdrant readiness.
     */
    public RagController(
            RagProperties ragProperties,
            RagCorpusService ragCorpusService,
            RagAnswerService ragAnswerService,
            QdrantStatusService qdrantStatusService
    ) {
        this.ragProperties = ragProperties;
        this.ragCorpusService = ragCorpusService;
        this.ragAnswerService = ragAnswerService;
        this.qdrantStatusService = qdrantStatusService;
    }

    /**
     * Retrieves the current status of the RAG system.
     *
     * @return A response containing status information, such as whether RAG is enabled,
     * if an index is loaded, and the number of documents/chunks.
     */
    @GetMapping("/status")
    public RagStatusResponse status() {
        RagCorpusService.CorpusSnapshot snapshot = ragCorpusService.snapshot();
        RagRetrievalMode mode = retrievalMode();
        QdrantStatus qdrantStatus = qdrantStatusService.status(ragProperties);
        return new RagStatusResponse(
                ragProperties.enabled(),
                snapshot != null,
                ragProperties.resolvedCorpusRoot().toString(),
                snapshot != null ? snapshot.documents().size() : 0,
                snapshot != null ? snapshot.chunks().size() : 0,
                mode.configValue(),
                mode.retrievalStore(),
                ragProperties.vectorStore(),
                ragProperties.qdrantUrl(),
                ragProperties.qdrantCollection(),
                qdrantStatus.required(),
                qdrantStatus.reachable(),
                qdrantStatus.collectionExists(),
                qdrantStatus.pointCount(),
                qdrantStatus.message(),
                ragProperties.embeddingProvider(),
                ragProperties.embeddingModel()
        );
    }

    /**
     * Rebuilds the RAG index by scanning the corpus root directory.
     * This operation may take some time depending on the size of the corpus.
     *
     * @return A response containing details of the newly created index.
     * @throws IllegalStateException if RAG is disabled.
     */
    @PostMapping("/index")
    public ResponseEntity<RagIndexResponse> index(@RequestBody(required = false) RagIndexRequest request) {
        ensureEnabled();
        RagRetrievalOptions options = retrievalOptions(request);
        RagCorpusService.CorpusSnapshot snapshot = ragCorpusService.rebuildIndex(options);
        return ResponseEntity.ok(new RagIndexResponse(
                snapshot.corpusRoot().toString(),
                snapshot.documents().size(),
                snapshot.chunks().size(),
                options.retrievalMode().configValue()
        ));
    }

    /**
     * Processes a RAG query to generate an answer based on the loaded corpus.
     *
     * @param request The query request containing the question and model details.
     * @return A response containing the generated answer and supporting context.
     * @throws IllegalStateException if RAG is disabled.
     */
    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(@Valid @RequestBody RagQueryRequest request) {
        ensureEnabled();
        RagRetrievalOptions options = retrievalOptions(request);
        return ResponseEntity.ok(ragAnswerService.answer(
                request.question(),
                request.provider(),
                request.model(),
                request.sessionId(),
                options
        ));
    }

    /**
     * Handles IllegalStateException by returning an appropriate HTTP error status and message.
     *
     * @param ex The exception that was thrown.
     * @return A response entity containing the error message.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        HttpStatus status = "RAG is disabled.".equals(ex.getMessage()) ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RagVectorRetrievalException.class)
    public ResponseEntity<Map<String, String>> handleVectorRetrieval(RagVectorRetrievalException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(RagVectorIndexingException.class)
    public ResponseEntity<Map<String, String>> handleVectorIndexing(RagVectorIndexingException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    /**
     * Ensures that the RAG system is enabled before proceeding with an operation.
     *
     * @throws IllegalStateException if RAG is disabled in the configuration.
     */
    private void ensureEnabled() {
        if (!ragProperties.enabled()) {
            throw new IllegalStateException("RAG is disabled.");
        }
    }

    private RagRetrievalMode retrievalMode() {
        return RagRetrievalMode.fromConfig(ragProperties.retrievalMode());
    }

    private RagRetrievalOptions retrievalOptions(RagQueryRequest request) {
        return RagRetrievalOptions.fromRequest(ragProperties, request.retrievalMode(), request.vectorStore());
    }

    private RagRetrievalOptions retrievalOptions(RagIndexRequest request) {
        if (request == null) {
            return RagRetrievalOptions.fromConfig(ragProperties);
        }
        return RagRetrievalOptions.fromRequest(ragProperties, request.retrievalMode(), request.vectorStore());
    }
}
