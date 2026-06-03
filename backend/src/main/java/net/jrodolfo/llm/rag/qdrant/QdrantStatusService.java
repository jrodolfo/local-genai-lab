package net.jrodolfo.llm.rag.qdrant;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.config.RagRetrievalMode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Performs lightweight readiness checks for the optional Qdrant vector store.
 */
@Service
public class QdrantStatusService {

    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient;

    public QdrantStatusService() {
        this(HttpClient.newBuilder()
                .connectTimeout(STATUS_TIMEOUT)
                .build());
    }

    QdrantStatusService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public QdrantStatus status(RagProperties ragProperties) {
        if (!isQdrantRequired(ragProperties)) {
            return QdrantStatus.notRequired();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ragProperties.qdrantUrl()))
                    .timeout(STATUS_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                return QdrantStatus.reachableStatus();
            }
            return QdrantStatus.unavailable(ragProperties.qdrantUrl());
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return QdrantStatus.unavailable(ragProperties.qdrantUrl());
        }
    }

    private boolean isQdrantRequired(RagProperties ragProperties) {
        if (!ragProperties.enabled()) {
            return false;
        }
        RagRetrievalMode mode = RagRetrievalMode.fromConfig(ragProperties.retrievalMode());
        return mode == RagRetrievalMode.VECTOR
                && "qdrant".equals(ragProperties.vectorStore().toLowerCase(Locale.ROOT).trim());
    }
}
