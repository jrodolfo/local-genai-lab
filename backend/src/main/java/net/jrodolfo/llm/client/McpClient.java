package net.jrodolfo.llm.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.McpProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class McpClient {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper objectMapper;
    private final McpProperties properties;

    public McpClient(ObjectMapper objectMapper, McpProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<McpToolDescriptor> listTools() {
        ensureEnabled();
        try (McpSession session = openSession()) {
            JsonNode response = session.sendRequest("tools/list", objectMapper.createObjectNode());
            JsonNode toolsNode = response.path("result").path("tools");
            if (!toolsNode.isArray()) {
                throw new McpClientException("MCP tools/list response did not contain a tools array.");
            }

            List<McpToolDescriptor> tools = new ArrayList<>();
            for (JsonNode toolNode : toolsNode) {
                tools.add(new McpToolDescriptor(
                        toolNode.path("name").asText(),
                        toolNode.path("title").asText(null),
                        toolNode.path("description").asText(null),
                        objectMapper.convertValue(toolNode.path("inputSchema"), Map.class)
                ));
            }
            return tools;
        } catch (IOException ex) {
            throw new McpClientException("Failed to list MCP tools.", ex);
        }
    }

    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        ensureEnabled();
        try (McpSession session = openSession()) {
            JsonNode requestBody = objectMapper.createObjectNode()
                    .put("name", toolName)
                    .set("arguments", objectMapper.valueToTree(arguments));

            JsonNode response = session.sendRequest("tools/call", requestBody);
            JsonNode resultNode = response.path("result");
            JsonNode structuredContent = resultNode.path("structuredContent");

            if (structuredContent.isObject()) {
                return objectMapper.convertValue(structuredContent, Map.class);
            }

            JsonNode contentNode = resultNode.path("content");
            if (contentNode.isArray() && !contentNode.isEmpty()) {
                JsonNode firstItem = contentNode.get(0);
                if (firstItem.hasNonNull("text")) {
                    try {
                        return objectMapper.readValue(firstItem.path("text").asText(), Map.class);
                    } catch (JsonProcessingException ignored) {
                        return Map.of(
                                "ok", !resultNode.path("isError").asBoolean(false),
                                "tool", toolName,
                                "text", firstItem.path("text").asText()
                        );
                    }
                }
            }

            throw new McpClientException("MCP tools/call response did not contain structured content.");
        } catch (IOException ex) {
            throw new McpClientException("Failed to call MCP tool: " + toolName, ex);
        }
    }

    private McpSession openSession() throws IOException {
        List<String> command = new ArrayList<>();
        command.add(properties.command());
        command.addAll(properties.args());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(Path.of(properties.workingDirectory()).toFile());

        Process process = processBuilder.start();
        McpSession session = new McpSession(
                process,
                Duration.ofSeconds(properties.startupTimeoutSeconds()),
                Duration.ofSeconds(properties.toolTimeoutSeconds())
        );
        session.initialize();
        return session;
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw new McpClientException("MCP integration is disabled.");
        }
    }

    public record McpToolDescriptor(
            String name,
            String title,
            String description,
            Map<String, Object> inputSchema
    ) {
    }

    private final class McpSession implements Closeable {
        private final Process process;
        private final BufferedReader inputReader;
        private final OutputStream outputStream;
        private final ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        private final ExecutorService stderrExecutor;
        private final Future<?> stderrReader;
        private final Duration startupTimeout;
        private final Duration requestTimeout;
        private long requestId = 0L;

        private McpSession(Process process, Duration startupTimeout, Duration requestTimeout) {
            this.process = process;
            this.startupTimeout = startupTimeout;
            this.requestTimeout = requestTimeout;
            this.inputReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            this.outputStream = process.getOutputStream();
            this.stderrExecutor = Executors.newSingleThreadExecutor();
            this.stderrReader = stderrExecutor.submit(() -> copyStderr(process.getErrorStream()));
        }

        private void initialize() {
            var initParams = objectMapper.createObjectNode();
            initParams.put("protocolVersion", PROTOCOL_VERSION);
            initParams.set("capabilities", objectMapper.createObjectNode());
            initParams.set("clientInfo", objectMapper.createObjectNode()
                    .put("name", "llm-pet-project-backend")
                    .put("version", "0.1.0"));

            JsonNode response = sendRequest("initialize", initParams, startupTimeout);
            JsonNode serverProtocol = response.path("result").path("protocolVersion");
            if (serverProtocol.isMissingNode()) {
                throw new McpClientException("MCP initialize response did not contain protocolVersion.");
            }

            sendNotification("notifications/initialized", objectMapper.createObjectNode());
        }

        private JsonNode sendRequest(String method, JsonNode params) {
            return sendRequest(method, params, requestTimeout);
        }

        private JsonNode sendRequest(String method, JsonNode params, Duration timeout) {
            long id = ++requestId;
            var request = objectMapper.createObjectNode();
            request.put("jsonrpc", JSON_RPC_VERSION);
            request.put("id", id);
            request.put("method", method);
            request.set("params", params);

            writeMessage(request);
            JsonNode response = readMessage(timeout);

            if (response.has("error")) {
                throw new McpClientException("MCP request failed for method " + method + ": " + response.path("error"));
            }

            if (!Objects.equals(response.path("id").asLong(), id)) {
                throw new McpClientException("MCP response id did not match request id for method " + method + ".");
            }

            return response;
        }

        private void sendNotification(String method, JsonNode params) {
            JsonNode request = objectMapper.createObjectNode()
                    .put("jsonrpc", JSON_RPC_VERSION)
                    .put("method", method)
                    .set("params", params);
            writeMessage(request);
        }

        private void writeMessage(JsonNode payload) {
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(payload);
                outputStream.write(bytes);
                outputStream.write('\n');
                outputStream.flush();
            } catch (IOException ex) {
                throw new McpClientException("Failed to write MCP message.", ex);
            }
        }

        private JsonNode readMessage(Duration timeout) {
            try (var executor = Executors.newSingleThreadExecutor()) {
                Future<JsonNode> future = executor.submit(this::readMessageBlocking);
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException ex) {
                throw new McpClientException("Timed out waiting for MCP response.", ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new McpClientException("Failed to read MCP response.", cause);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new McpClientException("Interrupted while waiting for MCP response.", ex);
            }
        }

        private JsonNode readMessageBlocking() {
            try {
                String line = inputReader.readLine();
                if (line == null || line.isBlank()) {
                    throw new McpClientException("No MCP response line was received.");
                }
                return objectMapper.readTree(line);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private void copyStderr(InputStream stderrStream) {
            try (stderrStream; stderrBuffer) {
                stderrStream.transferTo(stderrBuffer);
            } catch (IOException ignored) {
            }
        }

        @Override
        public void close() {
            try {
                inputReader.close();
            } catch (IOException ignored) {
            }
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
            process.destroy();
            try {
                process.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            try {
                stderrReader.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ignored) {
            }
            stderrExecutor.shutdownNow();
        }
    }
}
