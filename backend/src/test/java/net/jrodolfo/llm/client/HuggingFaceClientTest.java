package net.jrodolfo.llm.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.HuggingFaceProperties;
import org.junit.jupiter.api.Test;

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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HuggingFaceClientTest {

    @Test
    void discoverUsableModelsReusesCacheForEquivalentCandidateSet() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.addResponse("meta-llama/Llama-3.1-8B-Instruct", 200, "{}");
        httpClient.addResponse("Qwen/Qwen2.5-72B-Instruct", 404, "{\"error\":\"not available\"}");

        HuggingFaceClient client = new HuggingFaceClient(
                new ObjectMapper(),
                properties(),
                httpClient
        );

        List<String> first = client.discoverUsableModels(List.of(
                "meta-llama/Llama-3.1-8B-Instruct",
                "Qwen/Qwen2.5-72B-Instruct"
        ));
        List<String> second = client.discoverUsableModels(List.of(
                "Qwen/Qwen2.5-72B-Instruct",
                "meta-llama/Llama-3.1-8B-Instruct"
        ));

        assertEquals(List.of("meta-llama/Llama-3.1-8B-Instruct"), first);
        assertEquals(List.of("meta-llama/Llama-3.1-8B-Instruct"), second);
        assertEquals(2, httpClient.sendCount());
    }

    @Test
    void discoverUsableModelsSnapshotReusesCheckedAtWhileCacheIsFresh() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.addResponse("meta-llama/Llama-3.1-8B-Instruct", 200, "{}");
        MutableClock clock = new MutableClock(Instant.parse("2026-04-19T00:00:00Z"));

        HuggingFaceClient client = new HuggingFaceClient(
                new ObjectMapper(),
                properties(),
                httpClient,
                clock,
                Duration.ofSeconds(30)
        );

        HuggingFaceClient.DiscoverySnapshot first = client.discoverUsableModelsSnapshot(List.of("meta-llama/Llama-3.1-8B-Instruct"));
        clock.set(Instant.parse("2026-04-19T00:00:10Z"));
        HuggingFaceClient.DiscoverySnapshot second = client.discoverUsableModelsSnapshot(List.of("meta-llama/Llama-3.1-8B-Instruct"));

        assertEquals(first.checkedAt(), second.checkedAt());
        assertEquals(1, httpClient.sendCount());
    }

    @Test
    void discoverUsableModelsInvalidatesCacheWhenCandidateSetExpands() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.addResponse("meta-llama/Llama-3.1-8B-Instruct", 200, "{}");
        httpClient.addResponse("meta-llama/Llama-3.1-8B-Instruct", 200, "{}");
        httpClient.addResponse("Qwen/Qwen2.5-72B-Instruct", 200, "{}");

        HuggingFaceClient client = new HuggingFaceClient(
                new ObjectMapper(),
                properties(),
                httpClient
        );

        List<String> first = client.discoverUsableModels(List.of("meta-llama/Llama-3.1-8B-Instruct"));
        List<String> second = client.discoverUsableModels(List.of(
                "meta-llama/Llama-3.1-8B-Instruct",
                "Qwen/Qwen2.5-72B-Instruct"
        ));

        assertEquals(List.of("meta-llama/Llama-3.1-8B-Instruct"), first);
        assertEquals(List.of("meta-llama/Llama-3.1-8B-Instruct", "Qwen/Qwen2.5-72B-Instruct"), second);
        assertEquals(3, httpClient.sendCount());
    }

    @Test
    void chatClassifiesAuthenticationFailuresClearly() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        httpClient.addResponse(
                "meta-llama/Llama-3.1-8B-Instruct",
                401,
                "{\"error\":{\"message\":\"invalid token\"}}"
        );

        HuggingFaceClient client = new HuggingFaceClient(
                new ObjectMapper(),
                properties(),
                httpClient
        );

        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> client.chat(List.of(), "meta-llama/Llama-3.1-8B-Instruct")
        );

        assertEquals(
                "Hugging Face authentication failed. Check HUGGINGFACE_API_TOKEN and model access. Provider message: invalid token",
                exception.getMessage()
        );
    }

    @Test
    void chatClassifiesTimeoutFailuresClearly() {
        TimeoutHttpClient httpClient = new TimeoutHttpClient();

        HuggingFaceClient client = new HuggingFaceClient(
                new ObjectMapper(),
                properties(),
                httpClient
        );

        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> client.chat(List.of(), "meta-llama/Llama-3.1-8B-Instruct")
        );

        assertEquals(
                "Hugging Face request timed out after 60s. Try again or increase HUGGINGFACE_READ_TIMEOUT_SECONDS.",
                exception.getMessage()
        );
    }

    private HuggingFaceProperties properties() {
        return new HuggingFaceProperties(
                "https://router.huggingface.co/v1/chat/completions",
                "token",
                "meta-llama/Llama-3.1-8B-Instruct",
                List.of(
                        "meta-llama/Llama-3.1-8B-Instruct",
                        "Qwen/Qwen2.5-72B-Instruct"
                ),
                10,
                60
        );
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final java.util.Map<String, java.util.ArrayDeque<FakeHttpResponse<String>>> responsesByModel = new java.util.HashMap<>();
        private final AtomicInteger sendCount = new AtomicInteger();

        void addResponse(String model, int statusCode, String body) {
            responsesByModel.computeIfAbsent(model, ignored -> new java.util.ArrayDeque<>())
                    .add(new FakeHttpResponse<>(statusCode, body, URI.create("https://router.huggingface.co/v1/chat/completions")));
        }

        int sendCount() {
            return sendCount.get();
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
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            sendCount.incrementAndGet();
            String requestBody = readBody(request);
            String model = extractModel(requestBody);
            java.util.ArrayDeque<FakeHttpResponse<String>> responses = responsesByModel.get(model);
            if (responses == null || responses.isEmpty()) {
                throw new IOException("No fake response configured for model " + model);
            }
            FakeHttpResponse<String> response = responses.removeFirst();
            @SuppressWarnings("unchecked")
            HttpResponse<T> cast = (HttpResponse<T>) response;
            return cast;
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

        private String readBody(HttpRequest request) throws IOException {
            HttpRequest.BodyPublisher bodyPublisher = request.bodyPublisher()
                    .orElseThrow(() -> new IOException("Missing request body"));
            BodyCaptureSubscriber subscriber = new BodyCaptureSubscriber();
            bodyPublisher.subscribe(subscriber);
            return subscriber.body();
        }

        private String extractModel(String requestBody) throws IOException {
            JsonNodeWrapper root = JsonNodeWrapper.parse(requestBody);
            return root.model();
        }
    }

    private static final class TimeoutHttpClient extends HttpClient {
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
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            throw new HttpTimeoutException("timed out");
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

    private static final class MutableClock extends java.time.Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void set(Instant instant) {
            this.current = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static final class BodyCaptureSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
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
            output.write(bytes, 0, bytes.length);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        @Override
        public void onComplete() {
            if (subscription != null) {
                subscription.cancel();
            }
        }

        String body() {
            return output.toString(StandardCharsets.UTF_8);
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

    private record JsonNodeWrapper(String model) {
        static JsonNodeWrapper parse(String body) throws IOException {
            var root = new ObjectMapper().readTree(body);
            return new JsonNodeWrapper(root.path("model").asText());
        }
    }
}
