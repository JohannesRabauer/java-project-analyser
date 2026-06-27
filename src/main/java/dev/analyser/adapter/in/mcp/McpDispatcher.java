package dev.analyser.adapter.in.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.in.mcp.McpProtocol.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class McpDispatcher {

    private static final Logger LOG = Logger.getLogger(McpDispatcher.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, McpToolHandler> handlers;

    @Inject
    public McpDispatcher(Instance<McpToolHandler> handlerBeans) {
        this.handlers = handlerBeans.stream()
                .collect(Collectors.toMap(McpToolHandler::toolName, h -> h));
    }

    // For testing: direct injection of handlers
    public McpDispatcher(List<McpToolHandler> handlerList) {
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(McpToolHandler::toolName, h -> h));
    }

    public String dispatch(String rawJson) {
        JsonNode id = null;
        try {
            McpRequest request = mapper.readValue(rawJson, McpRequest.class);
            id = request.id();
            McpResponse response = processRequest(request);
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32700,\"message\":\"Parse error\"}}";
        } catch (Exception e) {
            return String.format("{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":{\"code\":-32603,\"message\":\"Internal error: %s\"}}",
                    id, e.getMessage());
        }
    }

    private McpResponse processRequest(McpRequest request) {
        if (!"2.0".equals(request.jsonrpc())) {
            return McpResponse.error(request.id(), -32600, "Invalid Request: jsonrpc must be 2.0");
        }
        if (request.method() == null) {
            return McpResponse.error(request.id(), -32600, "Invalid Request: missing method");
        }

        return switch (request.method()) {
            case "initialize" -> handleInitialize(request);
            case "notifications/initialized" -> McpResponse.success(request.id(), Map.of());
            case "tools/list" -> handleToolsList(request);
            case "tools/call" -> handleToolsCall(request);
            default -> McpResponse.error(request.id(), -32601, "Method not found: " + request.method());
        };
    }

    private McpResponse handleInitialize(McpRequest request) {
        var info = new ServerInfo("Java-Project-Analyser-MCP", "0.1.0");
        var capabilities = new ServerCapabilities(
                Map.of("listChanged", true),
                Map.of(),
                Map.of());
        var result = new InitializeResult(McpProtocol.PROTOCOL_VERSION, capabilities, info);
        return McpResponse.success(request.id(), result);
    }

    private McpResponse handleToolsList(McpRequest request) {
        List<McpTool> tools = handlers.values().stream()
                .map(h -> new McpTool(h.toolName(), h.description(), h.inputSchema()))
                .toList();
        return McpResponse.success(request.id(), new ToolsListResult(tools, null));
    }

    private McpResponse handleToolsCall(McpRequest request) {
        JsonNode params = request.params();
        if (params == null || !params.has("name")) {
            return McpResponse.error(request.id(), -32602, "Invalid params for tools/call");
        }

        String toolName = params.get("name").asText();
        JsonNode args = params.get("arguments");

        McpToolHandler handler = handlers.get(toolName);
        if (handler == null) {
            return McpResponse.error(request.id(), -32601, "Tool not found: " + toolName);
        }

        try {
            ToolCallResult result = handler.handle(args);
            return McpResponse.success(request.id(), result);
        } catch (Exception e) {
            LOG.errorf(e, "Exception during MCP tool call '%s'", toolName);
            return McpResponse.success(request.id(),
                    ToolCallResult.error("Exception during tool call: " + e.getMessage()));
        }
    }
}
