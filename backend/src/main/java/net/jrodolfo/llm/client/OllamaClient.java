package net.jrodolfo.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.OllamaProperties;
import net.jrodolfo.llm.provider.ProviderPromptMessage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
public class OllamaClient {

    private final ObjectMapper objectMapper;
    private final OllamaProperties properties;
    private final HttpClient httpClient;

    public OllamaClient(ObjectMapper objectMapper, OllamaProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .build();
    }

    public String generate(String prompt, String model) {
        try {
            HttpRequest request = buildGenerateRequest(prompt, model, false);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new OllamaClientException("Ollama request failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode payload = objectMapper.readTree(response.body());
            JsonNode textNode = payload.get("response");
            if (textNode == null) {
                throw new OllamaClientException("Ollama response did not contain 'response' field.");
            }
            return textNode.asText();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaClientException("Failed to call Ollama non-stream endpoint.", ex);
        } catch (IOException ex) {
            throw new OllamaClientException("Failed to call Ollama non-stream endpoint.", ex);
        }
    }

    public String chat(List<ProviderPromptMessage> messages, String model) {
        try {
            HttpRequest request = buildChatRequest(messages, model, false);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new OllamaClientException("Ollama chat request failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode payload = objectMapper.readTree(response.body());
            JsonNode textNode = payload.path("message").path("content");
            if (textNode.isMissingNode()) {
                throw new OllamaClientException("Ollama chat response did not contain 'message.content' field.");
            }
            return textNode.asText();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaClientException("Failed to call Ollama chat endpoint.", ex);
        } catch (IOException ex) {
            throw new OllamaClientException("Failed to call Ollama chat endpoint.", ex);
        }
    }

    public void streamGenerate(String prompt, String model, Consumer<String> tokenConsumer) {
        try {
            HttpRequest request = buildGenerateRequest(prompt, model, true);
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() >= 400) {
                throw new OllamaClientException("Ollama stream failed with status " + response.statusCode() + ".");
            }

            try (Stream<String> lines = response.body()) {
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode chunk = objectMapper.readTree(line);
                    JsonNode responseNode = chunk.get("response");
                    if (responseNode != null) {
                        tokenConsumer.accept(responseNode.asText());
                    }
                    JsonNode doneNode = chunk.get("done");
                    if (doneNode != null && doneNode.asBoolean()) {
                        break;
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaClientException("Failed to call Ollama stream endpoint.", ex);
        } catch (IOException ex) {
            throw new OllamaClientException("Failed to call Ollama stream endpoint.", ex);
        }
    }

    public void streamChat(List<ProviderPromptMessage> messages, String model, Consumer<String> tokenConsumer) {
        try {
            HttpRequest request = buildChatRequest(messages, model, true);
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() >= 400) {
                throw new OllamaClientException("Ollama chat stream failed with status " + response.statusCode() + ".");
            }

            try (Stream<String> lines = response.body()) {
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode chunk = objectMapper.readTree(line);
                    JsonNode responseNode = chunk.path("message").path("content");
                    if (!responseNode.isMissingNode()) {
                        tokenConsumer.accept(responseNode.asText());
                    }
                    JsonNode doneNode = chunk.get("done");
                    if (doneNode != null && doneNode.asBoolean()) {
                        break;
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaClientException("Failed to call Ollama chat stream endpoint.", ex);
        } catch (IOException ex) {
            throw new OllamaClientException("Failed to call Ollama chat stream endpoint.", ex);
        }
    }

    public List<String> listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(properties.readTimeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new OllamaClientException("Ollama model list request failed with status " + response.statusCode() + ".");
            }

            JsonNode payload = objectMapper.readTree(response.body());
            JsonNode modelsNode = payload.get("models");
            if (modelsNode == null || !modelsNode.isArray()) {
                return List.of();
            }

            List<String> models = new ArrayList<>();
            for (JsonNode modelNode : modelsNode) {
                JsonNode nameNode = modelNode.get("name");
                if (nameNode != null && !nameNode.asText().isBlank()) {
                    models.add(nameNode.asText());
                }
            }
            return models;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaClientException("Failed to call Ollama model list endpoint.", ex);
        } catch (IOException ex) {
            throw new OllamaClientException("Failed to call Ollama model list endpoint.", ex);
        }
    }

    private HttpRequest buildGenerateRequest(String prompt, String model, boolean stream) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", resolveModel(model));
        body.put("prompt", prompt);
        body.put("stream", stream);

        return HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/api/generate"))
                .timeout(Duration.ofSeconds(properties.readTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
    }

    private HttpRequest buildChatRequest(List<ProviderPromptMessage> messages, String model, boolean stream) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", resolveModel(model));
        body.put("stream", stream);
        body.put("messages", messages.stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList());

        return HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/api/chat"))
                .timeout(Duration.ofSeconds(properties.readTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
    }

    public String resolveModel(String model) {
        if (model == null || model.isBlank()) {
            return properties.defaultModel();
        }
        return model.trim();
    }
}
