package net.jrodolfo.llm.rag.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.rag.config.RagProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

/**
 * Ollama-backed embedding service for future vector-backed RAG retrieval.
 */
@Service
public class OllamaEmbeddingService implements EmbeddingService {

    private final ObjectMapper objectMapper;
    private final OllamaProperties ollamaProperties;
    private final RagProperties ragProperties;
    private final HttpClient httpClient;

    @Autowired
    public OllamaEmbeddingService(
            ObjectMapper objectMapper,
            OllamaProperties ollamaProperties,
            RagProperties ragProperties
    ) {
        this(
                objectMapper,
                ollamaProperties,
                ragProperties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(ollamaProperties.connectTimeoutSeconds()))
                        .build()
        );
    }

    OllamaEmbeddingService(
            ObjectMapper objectMapper,
            OllamaProperties ollamaProperties,
            RagProperties ragProperties,
            HttpClient httpClient
    ) {
        this.objectMapper = objectMapper;
        this.ollamaProperties = ollamaProperties;
        this.ragProperties = ragProperties;
        this.httpClient = httpClient;
    }

    @Override
    public EmbeddingVector embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed must not be blank.");
        }

        String model = resolveEmbeddingModel();
        try {
            HttpRequest request = buildRequest(model, text.trim());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new EmbeddingException("Ollama embedding request failed with status "
                        + response.statusCode() + ": " + response.body());
            }

            OllamaEmbeddingResponse payload = objectMapper.readValue(response.body(), OllamaEmbeddingResponse.class);
            List<Double> embedding = payload.embedding();
            if (embedding == null || embedding.isEmpty()) {
                throw new EmbeddingException("Ollama embedding response did not contain a usable embedding vector.");
            }
            return new EmbeddingVector(model, embedding);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException(buildRequestFailureMessage(ex), ex);
        } catch (IOException ex) {
            throw new EmbeddingException(buildRequestFailureMessage(ex), ex);
        }
    }

    private HttpRequest buildRequest(String model, String text) throws IOException {
        OllamaEmbeddingRequest body = new OllamaEmbeddingRequest(model, text);
        return HttpRequest.newBuilder()
                .uri(URI.create(ollamaProperties.baseUrl() + "/api/embeddings"))
                .timeout(Duration.ofSeconds(ollamaProperties.readTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
    }

    private String resolveEmbeddingModel() {
        String provider = ragProperties.embeddingProvider();
        if (provider == null || !provider.trim().equalsIgnoreCase("ollama")) {
            throw new EmbeddingException("Unsupported RAG embedding provider for Ollama embedding service: " + provider);
        }
        String model = ragProperties.embeddingModel();
        if (model == null || model.isBlank()) {
            throw new EmbeddingException("RAG embedding model must be configured.");
        }
        return model.trim();
    }

    private String buildRequestFailureMessage(Exception ex) {
        if (ex instanceof HttpTimeoutException) {
            return "Ollama embedding request timed out after " + ollamaProperties.readTimeoutSeconds()
                    + "s. Check whether the embedding model is available or increase OLLAMA_READ_TIMEOUT_SECONDS.";
        }
        String causeMessage = ex.getMessage();
        if (causeMessage == null || causeMessage.isBlank()) {
            return "Failed to call Ollama embedding endpoint.";
        }
        return "Failed to call Ollama embedding endpoint: " + causeMessage;
    }
}
