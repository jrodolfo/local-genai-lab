package net.jrodolfo.llm.rag.qdrant;

import net.jrodolfo.llm.rag.config.RagProperties;
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
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantStatusServiceTest {

    @Test
    void reportsNotRequiredForLexicalMode() {
        QdrantStatus status = new QdrantStatusService(new FakeHttpClient(200))
                .status(new RagProperties(true, "docs", 900, 160, 4, "lexical", "qdrant", "http://localhost:6333", "local_genai_lab_docs", "ollama", "nomic-embed-text"));

        assertFalse(status.required());
        assertNull(status.reachable());
        assertEquals("Qdrant is not required for the current RAG configuration.", status.message());
    }

    @Test
    void reportsReachableWhenQdrantReturnsSuccess() {
        QdrantStatus status = new QdrantStatusService(new FakeHttpClient(200))
                .status(new RagProperties(true, "docs", 900, 160, 4, "vector", "qdrant", "http://localhost:6333", "local_genai_lab_docs", "ollama", "nomic-embed-text"));

        assertTrue(status.required());
        assertTrue(status.reachable());
        assertEquals("Qdrant is reachable.", status.message());
    }

    @Test
    void reportsUnavailableWhenQdrantReturnsFailure() {
        QdrantStatus status = new QdrantStatusService(new FakeHttpClient(503))
                .status(new RagProperties(true, "docs", 900, 160, 4, "vector", "qdrant", "http://localhost:6333", "local_genai_lab_docs", "ollama", "nomic-embed-text"));

        assertTrue(status.required());
        assertFalse(status.reachable());
        assertEquals("Qdrant is not reachable at http://localhost:6333.", status.message());
    }

    @Test
    void reportsUnavailableWhenQdrantRequestFails() {
        QdrantStatus status = new QdrantStatusService(new FailingHttpClient())
                .status(new RagProperties(true, "docs", 900, 160, 4, "vector", "qdrant", "http://localhost:6333", "local_genai_lab_docs", "ollama", "nomic-embed-text"));

        assertTrue(status.required());
        assertFalse(status.reachable());
        assertEquals("Qdrant is not reachable at http://localhost:6333.", status.message());
    }

    @Test
    void reportsUnavailableWhenQdrantUrlIsInvalid() {
        QdrantStatus status = new QdrantStatusService(new FakeHttpClient(200))
                .status(new RagProperties(true, "docs", 900, 160, 4, "vector", "qdrant", "not a url", "local_genai_lab_docs", "ollama", "nomic-embed-text"));

        assertTrue(status.required());
        assertFalse(status.reachable());
        assertEquals("Qdrant is not reachable at not a url.", status.message());
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

    private static final class FakeHttpClient extends BaseHttpClient {
        private final int statusCode;

        private FakeHttpClient(int statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return new FakeHttpResponse<>(request.uri(), statusCode);
        }
    }

    private static final class FailingHttpClient extends BaseHttpClient {
        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            throw new IOException("connection refused");
        }
    }

    private record FakeHttpResponse<T>(URI uri, int statusCode) implements HttpResponse<T> {
        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(uri).build();
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
        public T body() {
            return null;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
