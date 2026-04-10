package net.jrodolfo.llm.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.McpProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientTest {

    @Test
    void listToolsReadsToolMetadataFromStdioServer() {
        McpClient client = new McpClient(new ObjectMapper(), fakeServerProperties());

        var tools = client.listTools();

        assertEquals(1, tools.size());
        assertEquals("list_recent_reports", tools.getFirst().name());
        assertEquals("List Recent Reports", tools.getFirst().title());
    }

    @Test
    void callToolReadsStructuredContentFromStdioServer() {
        McpClient client = new McpClient(new ObjectMapper(), fakeServerProperties());

        Map<String, Object> result = client.callTool("list_recent_reports", Map.of("limit", 2));

        assertEquals(Boolean.TRUE, result.get("ok"));
        assertEquals("list_recent_reports", result.get("tool"));
        assertTrue(result.containsKey("echo"));
    }

    private static McpProperties fakeServerProperties() {
        String script = """
                import readline from 'node:readline';

                const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });

                for await (const line of rl) {
                  if (!line.trim()) continue;
                  const message = JSON.parse(line);

                  if (message.method === 'initialize') {
                    console.log(JSON.stringify({
                      jsonrpc: '2.0',
                      id: message.id,
                      result: {
                        protocolVersion: '2024-11-05',
                        capabilities: {},
                        serverInfo: { name: 'fake-mcp', version: '1.0.0' }
                      }
                    }));
                    continue;
                  }

                  if (message.method === 'notifications/initialized') {
                    continue;
                  }

                  if (message.method === 'tools/list') {
                    console.log(JSON.stringify({
                      jsonrpc: '2.0',
                      id: message.id,
                      result: {
                        tools: [
                          {
                            name: 'list_recent_reports',
                            title: 'List Recent Reports',
                            description: 'Fake list tool',
                            inputSchema: { type: 'object' }
                          }
                        ]
                      }
                    }));
                    continue;
                  }

                  if (message.method === 'tools/call') {
                    console.log(JSON.stringify({
                      jsonrpc: '2.0',
                      id: message.id,
                      result: {
                        structuredContent: {
                          ok: true,
                          tool: message.params.name,
                          echo: message.params.arguments
                        },
                        content: [
                          {
                            type: 'text',
                            text: JSON.stringify({
                              ok: true,
                              tool: message.params.name,
                              echo: message.params.arguments
                            })
                          }
                        ]
                      }
                    }));
                  }
                }
                """;

        return new McpProperties(
                true,
                "node",
                List.of("--input-type=module", "-e", script),
                ".",
                5,
                5
        );
    }
}
