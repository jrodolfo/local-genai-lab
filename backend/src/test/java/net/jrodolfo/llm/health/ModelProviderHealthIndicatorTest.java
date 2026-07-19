package net.jrodolfo.llm.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.BedrockProperties;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import net.jrodolfo.llm.config.OllamaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelProviderHealthIndicatorTest {

    @Test
    void ollamaHealthStaysUpWhenDefaultModelIsMissing() {
        ModelProviderHealthIndicator indicator = new ModelProviderHealthIndicator(
                new AppModelProperties("ollama"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 1, 1),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", java.util.List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeHttpClient(200, "{\"models\":[{\"name\":\"mistral:7b\"}]}"),
                new ObjectMapper(),
                () -> true
        );

        var health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("not-ready", health.getDetails().get("status"));
        assertEquals(false, health.getDetails().get("modelPresent"));
        assertEquals(false, health.getDetails().get("ready"));
    }

    @Test
    void bedrockHealthIsDownWhenCredentialsCannotBeResolved() {
        ModelProviderHealthIndicator indicator = new ModelProviderHealthIndicator(
                new AppModelProperties("bedrock"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 1, 1),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", java.util.List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeHttpClient(200, "{\"models\":[]}"),
                new ObjectMapper(),
                () -> {
                    throw new IllegalStateException("no credentials");
                }
        );

        var health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(false, health.getDetails().get("credentialsResolved"));
    }

    @Test
    void huggingFaceHealthIsDownWhenTokenIsMissing() {
        ModelProviderHealthIndicator indicator = new ModelProviderHealthIndicator(
                new AppModelProperties("huggingface"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 1, 1),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "", "meta-llama/Llama-3.1-8B-Instruct", java.util.List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                new FakeHttpClient(200, "{\"models\":[]}"),
                new ObjectMapper(),
                () -> true
        );

        var health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("misconfigured", health.getDetails().get("status"));
        assertEquals(false, health.getDetails().get("tokenConfigured"));
    }

    @Test
    void ollamaHealthUsesNonNullErrorWhenReachabilityExceptionHasNoMessage() {
        ModelProviderHealthIndicator indicator = new ModelProviderHealthIndicator(
                new AppModelProperties("ollama"),
                new OllamaProperties("http://localhost:11434", "llama3:8b", 1, 1),
                new BedrockProperties("us-east-1", "amazon.nova-lite-v1:0"),
                new HuggingFaceProperties("https://router.huggingface.co/v1/chat/completions", "token", "meta-llama/Llama-3.1-8B-Instruct", java.util.List.of("meta-llama/Llama-3.1-8B-Instruct"), 10, 60),
                FakeHttpClient.failingWithMessageLessIOException(),
                new ObjectMapper(),
                () -> true
        );

        var health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("unreachable", health.getDetails().get("status"));
        assertEquals("IOException", health.getDetails().get("error"));
    }

    private static final class FakeHttpClient extends HttpClient {
        private final int statusCode;
        private final String body;
        private final boolean failWithMessageLessIOException;

        private FakeHttpClient(int statusCode, String body) {
            this(statusCode, body, false);
        }

        private FakeHttpClient(int statusCode, String body, boolean failWithMessageLessIOException) {
            this.statusCode = statusCode;
            this.body = body;
            this.failWithMessageLessIOException = failWithMessageLessIOException;
        }

        private static FakeHttpClient failingWithMessageLessIOException() {
            return new FakeHttpClient(0, "", true);
        }

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
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            if (failWithMessageLessIOException) {
                throw new IOException();
            }
            @SuppressWarnings("unchecked")
            T castBody = (T) body;
            return new FakeHttpResponse<>(statusCode, castBody, request.uri());
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeHttpResponse<T>(int statusCode, T body, URI uri) implements HttpResponse<T> {
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
            return HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
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
}
