package dev.analyser.demo.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class McpHttpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void uc001_shouldListResourcesViaMcpSse() throws Exception {
        try (FakeMcpServer server = new FakeMcpServer("""
                {
                  "jsonrpc": "2.0",
                  "id": "resources-id",
                  "result": {
                    "resources": [
                      {
                        "uri": "mcp://analysis/jobs"
                      }
                    ]
                  }
                }
                """)) {
            McpHttpClient client = new McpHttpClient(objectMapper);

            JsonNode response = client.listResources(server.baseUrl());

            assertEquals("mcp://analysis/jobs", response.get("result").get("resources").get(0).get("uri").asText());
            assertEquals("resources/list", server.recordedMethod());
        }
    }

    @Test
    void uc001_shouldReadJobsResourceViaMcpSse() throws Exception {
        try (FakeMcpServer server = new FakeMcpServer("""
                {
                  "jsonrpc": "2.0",
                  "id": "resource-read-id",
                  "result": {
                    "contents": [
                      {
                        "uri": "mcp://analysis/jobs",
                        "mimeType": "application/json",
                        "text": "[{\\"jobId\\":\\"11111111-1111-1111-1111-111111111111\\"}]"
                      }
                    ]
                  }
                }
                """)) {
            McpHttpClient client = new McpHttpClient(objectMapper);

            JsonNode response = client.readResource(server.baseUrl(), "mcp://analysis/jobs");

            assertTrue(response.get("result").get("contents").get(0).get("text").asText().contains("11111111-1111-1111-1111-111111111111"));
            assertEquals("resources/read", server.recordedMethod());
            assertEquals("mcp://analysis/jobs", server.recordedParams().get("uri").asText());
        }
    }

    @Test
    void uc004_shouldListPromptsViaMcpSse() throws Exception {
        try (FakeMcpServer server = new FakeMcpServer("""
                {
                  "jsonrpc": "2.0",
                  "id": "prompts-id",
                  "result": {
                    "prompts": [
                      {
                        "name": "review-codebase-risks"
                      }
                    ]
                  }
                }
                """)) {
            McpHttpClient client = new McpHttpClient(objectMapper);

            JsonNode response = client.listPrompts(server.baseUrl());

            assertEquals("review-codebase-risks", response.get("result").get("prompts").get(0).get("name").asText());
            assertEquals("prompts/list", server.recordedMethod());
        }
    }

    @Test
    void uc007_shouldFetchFeaturePromptViaMcpSse() throws Exception {
        try (FakeMcpServer server = new FakeMcpServer("""
                {
                  "jsonrpc": "2.0",
                  "id": "prompt-get-id",
                  "result": {
                    "description": "Use the MCP tools to retrieve architecture and class context before suggesting a feature implementation plan."
                  }
                }
                """)) {
            McpHttpClient client = new McpHttpClient(objectMapper);

            JsonNode response = client.getPrompt(server.baseUrl(), "suggest-feature-impl");

            assertTrue(response.get("result").get("description").asText().contains("feature implementation plan"));
            assertEquals("prompts/get", server.recordedMethod());
            assertEquals("suggest-feature-impl", server.recordedParams().get("name").asText());
        }
    }

    private final class FakeMcpServer implements AutoCloseable {

        private final HttpServer server;
        private final BlockingQueue<String> responseQueue = new ArrayBlockingQueue<>(1);
        private volatile JsonNode recordedRequest;

        private FakeMcpServer(String responsePayload) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/mcp/sse", this::handleSse);
            server.createContext("/mcp/message", exchange -> handleMessage(exchange, responsePayload));
            server.start();
        }

        private void handleSse(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeEvent(outputStream, "endpoint", "/mcp/message");
                try {
                    String response = responseQueue.poll(5, TimeUnit.SECONDS);
                    if (response == null) {
                        throw new IllegalStateException("Timed out waiting for MCP response payload");
                    }
                    writeEvent(outputStream, "message", response);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for queued MCP response", exception);
                }
            }
        }

        private void handleMessage(HttpExchange exchange, String responsePayload) throws IOException {
            recordedRequest = objectMapper.readTree(exchange.getRequestBody().readAllBytes());
            responseQueue.offer(responsePayload);
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        }

        private void writeEvent(OutputStream outputStream, String eventName, String data) throws IOException {
            outputStream.write(("event: " + eventName + "\n").getBytes(StandardCharsets.UTF_8));
            for (String line : data.split("\n", -1)) {
                outputStream.write(("data: " + line + "\n").getBytes(StandardCharsets.UTF_8));
            }
            outputStream.write('\n');
            outputStream.flush();
        }

        private String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        private String recordedMethod() {
            return recordedRequest.get("method").asText();
        }

        private JsonNode recordedParams() {
            return recordedRequest.get("params");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
