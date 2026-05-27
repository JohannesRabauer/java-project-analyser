package dev.analyser.demo.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
public class McpHttpClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public McpHttpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode listTools(String serverBaseUrl) {
        return dispatch(serverBaseUrl, rpcRequest("tools/list", objectMapper.createObjectNode()));
    }

    public String callTool(String serverBaseUrl, String toolName, ObjectNode arguments) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", arguments);

        JsonNode response = dispatch(serverBaseUrl, rpcRequest("tools/call", params));
        JsonNode content = response.path("result").path("content");
        if (!content.isArray() || content.isEmpty()) {
            throw new IllegalStateException("MCP response did not contain tool content");
        }
        return content.get(0).path("text").asText();
    }

    private String rpcRequest(String method, ObjectNode params) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", UUID.randomUUID().toString());
        request.put("method", method);
        request.set("params", params);
        return request.toString();
    }

    private JsonNode dispatch(String serverBaseUrl, String payload) {
        String normalizedBaseUrl = normalizeBaseUrl(serverBaseUrl);

        HttpRequest connectRequest = HttpRequest.newBuilder(URI.create(normalizedBaseUrl + "/mcp/sse"))
                .GET()
                .header("Accept", "text/event-stream")
                .timeout(REQUEST_TIMEOUT)
                .build();

        try {
            HttpResponse<InputStream> sseResponse = httpClient.send(connectRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (sseResponse.statusCode() != 200) {
                throw new IllegalStateException("Failed to open MCP SSE connection: HTTP " + sseResponse.statusCode());
            }

            try (InputStream body = sseResponse.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {

                String messageEndpoint = readEventData(reader, "endpoint");
                if (messageEndpoint == null || messageEndpoint.isBlank()) {
                    throw new IllegalStateException("MCP SSE handshake did not yield a message endpoint");
                }

                HttpRequest messageRequest = HttpRequest.newBuilder(resolveMessageEndpoint(normalizedBaseUrl, messageEndpoint))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .header("Content-Type", "application/json")
                        .timeout(REQUEST_TIMEOUT)
                        .build();

                HttpResponse<Void> messageResponse = httpClient.send(messageRequest, HttpResponse.BodyHandlers.discarding());
                if (messageResponse.statusCode() != 202) {
                    throw new IllegalStateException("MCP message endpoint rejected the request: HTTP " + messageResponse.statusCode());
                }

                String responsePayload = readEventData(reader, "message");
                if (responsePayload == null || responsePayload.isBlank()) {
                    throw new IllegalStateException("MCP SSE connection did not return a message payload");
                }

                return objectMapper.readTree(responsePayload);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to communicate with MCP server at " + normalizedBaseUrl, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while communicating with MCP server", exception);
        }
    }

    private String readEventData(BufferedReader reader, String expectedEventName) throws IOException {
        String currentEvent = null;
        StringBuilder data = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.startsWith("event:")) {
                currentEvent = line.substring("event:".length()).trim();
                continue;
            }
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
                continue;
            }
            if (line.isEmpty()) {
                if (expectedEventName.equals(currentEvent)) {
                    return data.toString();
                }
                currentEvent = null;
                data.setLength(0);
            }
        }

        return null;
    }

    private URI resolveMessageEndpoint(String normalizedBaseUrl, String endpoint) {
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return URI.create(endpoint);
        }
        URI baseUri = URI.create(normalizedBaseUrl);
        if (endpoint.startsWith("/")) {
            return URI.create(baseUri.getScheme() + "://" + baseUri.getAuthority() + endpoint);
        }
        return URI.create(normalizedBaseUrl + "/" + endpoint);
    }

    private String normalizeBaseUrl(String serverBaseUrl) {
        String trimmed = serverBaseUrl == null ? "" : serverBaseUrl.trim();
        if (trimmed.isEmpty()) {
            return "http://localhost:8080";
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
