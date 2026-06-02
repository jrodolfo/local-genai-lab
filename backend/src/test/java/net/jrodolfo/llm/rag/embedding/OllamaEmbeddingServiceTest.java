package net.jrodolfo.llm.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.rag.config.RagProperties;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaEmbeddingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void embedSendsConfiguredModelAndTextToOllamaEmbeddingsEndpoint() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"embedding":[0.125,-0.5,1.25]}
                """);
        OllamaEmbeddingService service = service(httpClient, "nomic-embed-text");

        EmbeddingVector vector = service.embed("  How are sessions persisted?  ");

        assertEquals("nomic-embed-text", vector.model());
        assertEquals(List.of(0.125, -0.5, 1.25), vector.values());
        assertEquals(URI.create("http://localhost:11434/api/embeddings"), httpClient.requestUri());

        JsonNode body = objectMapper.readTree(httpClient.requestBody());
        assertEquals("nomic-embed-text", body.get("model").asText());
        assertEquals("How are sessions persisted?", body.get("prompt").asText());
    }

    @Test
    void embedRejectsBlankTextBeforeCallingOllama() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"embedding":[1.0]}
                """);
        OllamaEmbeddingService service = service(httpClient, "nomic-embed-text");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.embed("  "));

        assertEquals("Text to embed must not be blank.", exception.getMessage());
        assertEquals(0, httpClient.sendCount());
    }

    @Test
    void embedRejectsMissingEmbeddingModelBeforeCallingOllama() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"embedding":[1.0]}
                """);
        OllamaEmbeddingService service = service(httpClient, "ollama", " ");

        EmbeddingException exception = assertThrows(EmbeddingException.class, () -> service.embed("hello"));

        assertEquals("RAG embedding model must be configured.", exception.getMessage());
        assertEquals(0, httpClient.sendCount());
    }

    @Test
    void embedRejectsUnsupportedEmbeddingProviderBeforeCallingOllama() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"embedding":[1.0]}
                """);
        OllamaEmbeddingService service = service(httpClient, "bedrock", "nomic-embed-text");

        EmbeddingException exception = assertThrows(EmbeddingException.class, () -> service.embed("hello"));

        assertEquals("Unsupported RAG embedding provider for Ollama embedding service: bedrock", exception.getMessage());
        assertEquals(0, httpClient.sendCount());
    }

    @Test
    void embedReportsHttpFailuresClearly() {
        RecordingHttpClient httpClient = new RecordingHttpClient(500, """
                {"error":"model unavailable"}
                """);
        OllamaEmbeddingService service = service(httpClient, "nomic-embed-text");

        EmbeddingException exception = assertThrows(EmbeddingException.class, () -> service.embed("hello"));

        assertTrue(exception.getMessage().contains("Ollama embedding request failed with status 500"));
        assertTrue(exception.getMessage().contains("model unavailable"));
    }

    @Test
    void embedRejectsResponsesWithoutUsableVector() {
        RecordingHttpClient httpClient = new RecordingHttpClient(200, """
                {"embedding":[]}
                """);
        OllamaEmbeddingService service = service(httpClient, "nomic-embed-text");

        EmbeddingException exception = assertThrows(EmbeddingException.class, () -> service.embed("hello"));

        assertEquals("Ollama embedding response did not contain a usable embedding vector.", exception.getMessage());
    }

    @Test
    void embedReportsTimeoutsWithOllamaGuidance() {
        OllamaEmbeddingService service = service(new TimeoutHttpClient(), "nomic-embed-text");

        EmbeddingException exception = assertThrows(EmbeddingException.class, () -> service.embed("hello"));

        assertTrue(exception.getMessage().contains("Ollama embedding request timed out after 60s"));
    }

    private OllamaEmbeddingService service(HttpClient httpClient, String embeddingModel) {
        return service(httpClient, "ollama", embeddingModel);
    }

    private OllamaEmbeddingService service(HttpClient httpClient, String embeddingProvider, String embeddingModel) {
        return new OllamaEmbeddingService(
                objectMapper,
                new OllamaProperties("http://localhost:11434", "llama3:8b", 10, 60),
                new RagProperties(true, "docs", 900, 160, 4, "lexical", embeddingProvider, embeddingModel),
                httpClient
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
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingHttpClient extends BaseHttpClient {
        private final int statusCode;
        private final String responseBody;
        private int sendCount;
        private URI requestUri;
        private String requestBody;

        private RecordingHttpClient(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        int sendCount() {
            return sendCount;
        }

        URI requestUri() {
            return requestUri;
        }

        String requestBody() {
            return requestBody;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            sendCount++;
            requestUri = request.uri();
            requestBody = readBody(request);

            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) new FakeHttpResponse(statusCode, responseBody, requestUri);
            return response;
        }

        private String readBody(HttpRequest request) throws IOException {
            HttpRequest.BodyPublisher bodyPublisher = request.bodyPublisher()
                    .orElseThrow(() -> new IOException("Missing request body"));
            BodyCaptureSubscriber subscriber = new BodyCaptureSubscriber();
            bodyPublisher.subscribe(subscriber);
            return subscriber.body();
        }
    }

    private static final class TimeoutHttpClient extends BaseHttpClient {

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            throw new HttpTimeoutException("timed out");
        }
    }

    private record FakeHttpResponse(int statusCode, String body, URI uri) implements HttpResponse<String> {

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (key, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private static final class BodyCaptureSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            outputStream.writeBytes(bytes);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new IllegalStateException(throwable);
        }

        @Override
        public void onComplete() {
            if (subscription != null) {
                subscription.cancel();
            }
        }

        String body() {
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
