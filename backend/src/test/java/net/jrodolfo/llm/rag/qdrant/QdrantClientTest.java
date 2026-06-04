package net.jrodolfo.llm.rag.qdrant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void collectionExistsChecksCollectionEndpoint() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("{}", 200));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        assertTrue(client.collectionExists("http://localhost:6333", "local_genai_lab_docs"));

        assertEquals("GET", httpClient.lastRequest.method());
        assertEquals(URI.create("http://localhost:6333/collections/local_genai_lab_docs"), httpClient.lastRequest.uri());
    }

    @Test
    void collectionExistsReturnsFalseForNotFound() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("{}", 404));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        assertFalse(client.collectionExists("http://localhost:6333", "missing"));
    }

    @Test
    void collectionExistsThrowsForUnexpectedFailure() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("{}", 500));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        QdrantClientException ex = assertThrows(
                QdrantClientException.class,
                () -> client.collectionExists("http://localhost:6333", "broken")
        );

        assertEquals("Failed to check Qdrant collection: HTTP 500.", ex.getMessage());
    }

    @Test
    void collectionInfoMapsPointCount() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("""
                {
                  "result": {
                    "points_count": 123
                  }
                }
                """, 200));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        QdrantCollectionInfo info = client.collectionInfo("http://localhost:6333", "local_genai_lab_docs");

        assertEquals("GET", httpClient.lastRequest.method());
        assertEquals(URI.create("http://localhost:6333/collections/local_genai_lab_docs"), httpClient.lastRequest.uri());
        assertTrue(info.exists());
        assertEquals(123L, info.pointCount());
    }

    @Test
    void collectionInfoReturnsMissingForNotFound() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("{}", 404));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        QdrantCollectionInfo info = client.collectionInfo("http://localhost:6333", "missing");

        assertFalse(info.exists());
        assertEquals(null, info.pointCount());
    }

    @Test
    void recreateCollectionUsesCosineVectorConfig() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                new FakeHttpResponse<>("{}", 200),
                new FakeHttpResponse<>("{}", 200)
        );
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        client.recreateCollection("http://localhost:6333", "local_genai_lab_docs", 768);

        assertEquals("DELETE", httpClient.requests.get(0).method());
        assertEquals(URI.create("http://localhost:6333/collections/local_genai_lab_docs"), httpClient.requests.get(0).uri());
        assertEquals("PUT", httpClient.requests.get(1).method());
        assertEquals(URI.create("http://localhost:6333/collections/local_genai_lab_docs"), httpClient.requests.get(1).uri());
        assertEquals(
                "{\"vectors\":{\"size\":768,\"distance\":\"Cosine\"}}",
                httpClient.bodies.get(1)
        );
    }

    @Test
    void recreateCollectionIgnoresMissingCollectionBeforeCreate() {
        RecordingHttpClient httpClient = new RecordingHttpClient(
                new FakeHttpResponse<>("{}", 404),
                new FakeHttpResponse<>("{}", 200)
        );
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        client.recreateCollection("http://localhost:6333", "local_genai_lab_docs", 768);

        assertEquals("DELETE", httpClient.requests.get(0).method());
        assertEquals("PUT", httpClient.requests.get(1).method());
    }

    @Test
    void deleteCollectionIgnoresMissingCollection() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("{}", 404));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        client.deleteCollection("http://localhost:6333", "missing");

        assertEquals("DELETE", httpClient.lastRequest.method());
        assertEquals(URI.create("http://localhost:6333/collections/missing"), httpClient.lastRequest.uri());
    }

    @Test
    void deleteCollectionThrowsForUnexpectedFailure() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("{}", 503));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        QdrantClientException ex = assertThrows(
                QdrantClientException.class,
                () -> client.deleteCollection("http://localhost:6333", "broken")
        );

        assertEquals("Failed to delete Qdrant collection: HTTP 503.", ex.getMessage());
    }

    @Test
    void upsertPointsSendsVectorsAndPayload() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("{}", 200));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        client.upsertPoints("http://localhost:6333", "local_genai_lab_docs", List.of(samplePoint()));

        assertEquals("PUT", httpClient.lastRequest.method());
        assertEquals(URI.create("http://localhost:6333/collections/local_genai_lab_docs/points?wait=true"), httpClient.lastRequest.uri());
        assertTrue(httpClient.lastBody.contains("\"id\":\"architecture.md#0\""));
        assertTrue(httpClient.lastBody.contains("\"vector\":[0.1,0.2,0.3]"));
        assertTrue(httpClient.lastBody.contains("\"source_path\":\"architecture.md\""));
        assertTrue(httpClient.lastBody.contains("\"embedding_model\":\"nomic-embed-text\""));
    }

    @Test
    void searchMapsQdrantResults() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("""
                {
                  "result": [
                    {
                      "id": "architecture.md#0",
                      "score": 0.91,
                      "payload": {
                        "source_path": "architecture.md",
                        "chunk_id": "architecture.md#0",
                        "title": "Architecture",
                        "text": "Provider registry text.",
                        "corpus_root": "docs",
                        "embedding_provider": "ollama",
                        "embedding_model": "nomic-embed-text",
                        "indexed_at": "2026-06-04T00:00:00Z",
                        "content_hash": "abc123"
                      }
                    }
                  ]
                }
                """, 200));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        List<QdrantSearchResult> results = client.search("http://localhost:6333", "local_genai_lab_docs", List.of(0.1, 0.2), 4);

        assertEquals("POST", httpClient.lastRequest.method());
        assertEquals(URI.create("http://localhost:6333/collections/local_genai_lab_docs/points/search"), httpClient.lastRequest.uri());
        assertEquals("{\"vector\":[0.1,0.2],\"limit\":4,\"withPayload\":true}", httpClient.lastBody);
        assertEquals(1, results.size());
        assertEquals("architecture.md#0", results.getFirst().id());
        assertEquals(0.91, results.getFirst().score());
        assertEquals("architecture.md", results.getFirst().payload().sourcePath());
        assertEquals("nomic-embed-text", results.getFirst().payload().embeddingModel());
    }

    @Test
    void searchThrowsClearExceptionForFailure() {
        RecordingHttpClient httpClient = new RecordingHttpClient(new FakeHttpResponse<>("{}", 503));
        QdrantClient client = new QdrantClient(objectMapper, httpClient);

        QdrantClientException ex = assertThrows(
                QdrantClientException.class,
                () -> client.search("http://localhost:6333", "local_genai_lab_docs", List.of(0.1), 4)
        );

        assertEquals("Failed to search Qdrant points: HTTP 503.", ex.getMessage());
    }

    @Test
    void upsertThrowsClearExceptionWhenRequestFails() {
        QdrantClient client = new QdrantClient(objectMapper, new FailingHttpClient());

        QdrantClientException ex = assertThrows(
                QdrantClientException.class,
                () -> client.upsertPoints("http://localhost:6333", "local_genai_lab_docs", List.of(samplePoint()))
        );

        assertEquals("Failed to upsert Qdrant points.", ex.getMessage());
    }

    private QdrantPoint samplePoint() {
        return new QdrantPoint(
                "architecture.md#0",
                List.of(0.1, 0.2, 0.3),
                new QdrantPointPayload(
                        "architecture.md",
                        "architecture.md#0",
                        "Architecture",
                        "Provider registry text.",
                        "docs",
                        "ollama",
                        "nomic-embed-text",
                        "2026-06-04T00:00:00Z",
                        "abc123"
                )
        );
    }

    private abstract static class BaseHttpClient extends HttpClient {
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingHttpClient extends BaseHttpClient {
        private final List<HttpResponse<String>> responses;
        private final List<HttpRequest> requests = new ArrayList<>();
        private final List<String> bodies = new ArrayList<>();
        private HttpRequest lastRequest;
        private String lastBody;
        private int responseIndex;

        @SafeVarargs
        private RecordingHttpClient(HttpResponse<String>... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            this.lastRequest = request;
            this.lastBody = readBody(request);
            this.requests.add(request);
            this.bodies.add(lastBody);
            HttpResponse<String> response = responses.get(Math.min(responseIndex, responses.size() - 1));
            responseIndex++;
            @SuppressWarnings("unchecked")
            HttpResponse<T> typedResponse = (HttpResponse<T>) response;
            return typedResponse;
        }
    }

    private static final class FailingHttpClient extends BaseHttpClient {
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            throw new IOException("connection refused");
        }
    }

    private static String readBody(HttpRequest request) throws InterruptedException {
        Optional<HttpRequest.BodyPublisher> publisher = request.bodyPublisher();
        if (publisher.isEmpty()) {
            return "";
        }

        BodySubscriber subscriber = new BodySubscriber();
        publisher.get().subscribe(subscriber);
        subscriber.awaitCompletion();
        return subscriber.body();
    }

    private static final class BodySubscriber implements Flow.Subscriber<ByteBuffer> {
        private final List<ByteBuffer> buffers = new ArrayList<>();
        private final CompletableFuture<Void> complete = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            buffers.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            complete.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            complete.complete(null);
        }

        private void awaitCompletion() throws InterruptedException {
            try {
                complete.get();
            } catch (java.util.concurrent.ExecutionException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private String body() {
            int size = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
            ByteBuffer merged = ByteBuffer.allocate(size);
            buffers.forEach(merged::put);
            merged.flip();
            return java.nio.charset.StandardCharsets.UTF_8.decode(merged).toString();
        }
    }

    private static final class FakeHttpResponse<T> implements HttpResponse<T> {
        private final String responseBody;
        private final int statusCode;

        private FakeHttpResponse(String responseBody, int statusCode) {
            this.responseBody = responseBody;
            this.statusCode = statusCode;
        }

        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create("http://localhost:6333")).build();
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        @SuppressWarnings("unchecked")
        public T body() {
            return (T) responseBody;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("http://localhost:6333");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
