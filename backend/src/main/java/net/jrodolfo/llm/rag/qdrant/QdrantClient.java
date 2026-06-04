package net.jrodolfo.llm.rag.qdrant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Minimal HTTP boundary for Qdrant collection, upsert, and search operations.
 */
@Component
public class QdrantClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public QdrantClient(ObjectMapper objectMapper) {
        this(
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(REQUEST_TIMEOUT)
                        .build()
        );
    }

    QdrantClient(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean collectionExists(String qdrantUrl, String collectionName) {
        HttpRequest request = requestBuilder(qdrantUrl, "/collections/" + collectionName)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return false;
            }
            ensureSuccess(response, "check Qdrant collection");
            return true;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new QdrantClientException("Failed to check Qdrant collection " + collectionName + ".", ex);
        }
    }

    public QdrantCollectionInfo collectionInfo(String qdrantUrl, String collectionName) {
        HttpRequest request = requestBuilder(qdrantUrl, "/collections/" + collectionName)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return QdrantCollectionInfo.missing();
            }
            ensureSuccess(response, "check Qdrant collection");
            JsonNode result = objectMapper.readTree(response.body()).path("result");
            Long pointCount = result.path("points_count").canConvertToLong()
                    ? result.path("points_count").longValue()
                    : null;
            return new QdrantCollectionInfo(true, pointCount);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new QdrantClientException("Failed to check Qdrant collection " + collectionName + ".", ex);
        }
    }

    public void recreateCollection(String qdrantUrl, String collectionName, int vectorSize) {
        QdrantCollectionRequest requestBody = new QdrantCollectionRequest(
                new QdrantVectorConfig(vectorSize, "Cosine")
        );
        sendWithoutResponse(
                putRequest(qdrantUrl, "/collections/" + collectionName, requestBody),
                "recreate Qdrant collection"
        );
    }

    public void upsertPoints(String qdrantUrl, String collectionName, List<QdrantPoint> points) {
        QdrantUpsertRequest requestBody = new QdrantUpsertRequest(points);
        sendWithoutResponse(
                putRequest(qdrantUrl, "/collections/" + collectionName + "/points?wait=true", requestBody),
                "upsert Qdrant points"
        );
    }

    public List<QdrantSearchResult> search(String qdrantUrl, String collectionName, List<Double> queryVector, int topK) {
        QdrantSearchRequest requestBody = new QdrantSearchRequest(queryVector, topK, true);
        HttpRequest request = postRequest(qdrantUrl, "/collections/" + collectionName + "/points/search", requestBody);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, "search Qdrant points");
            JsonNode result = objectMapper.readTree(response.body()).path("result");
            if (!result.isArray()) {
                return List.of();
            }
            return objectMapper.readerForListOf(QdrantSearchResult.class).readValue(result);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new QdrantClientException("Failed to search Qdrant points.", ex);
        }
    }

    private void sendWithoutResponse(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response, action);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new QdrantClientException("Failed to " + action + ".", ex);
        }
    }

    private HttpRequest putRequest(String qdrantUrl, String path, Object body) {
        return requestBuilder(qdrantUrl, path)
                .PUT(jsonBody(body))
                .build();
    }

    private HttpRequest postRequest(String qdrantUrl, String path, Object body) {
        return requestBuilder(qdrantUrl, path)
                .POST(jsonBody(body))
                .build();
    }

    private HttpRequest.Builder requestBuilder(String qdrantUrl, String path) {
        return HttpRequest.newBuilder(resolveUri(qdrantUrl, path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json");
    }

    private URI resolveUri(String qdrantUrl, String path) {
        String normalizedBase = qdrantUrl.endsWith("/") ? qdrantUrl.substring(0, qdrantUrl.length() - 1) : qdrantUrl;
        return URI.create(normalizedBase + path);
    }

    private HttpRequest.BodyPublisher jsonBody(Object body) {
        try {
            return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException ex) {
            throw new QdrantClientException("Failed to serialize Qdrant request body.", ex);
        }
    }

    private void ensureSuccess(HttpResponse<String> response, String action) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new QdrantClientException(
                    "Failed to " + action + ": HTTP " + response.statusCode() + "."
            );
        }
    }

    private record QdrantCollectionRequest(QdrantVectorConfig vectors) {
    }

    private record QdrantVectorConfig(int size, String distance) {
    }

    private record QdrantUpsertRequest(List<QdrantPoint> points) {
    }

    private record QdrantSearchRequest(List<Double> vector, int limit, boolean withPayload) {
    }
}
