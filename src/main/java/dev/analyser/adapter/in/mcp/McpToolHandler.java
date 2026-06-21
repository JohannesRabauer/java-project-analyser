package dev.analyser.adapter.in.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;

import java.util.Map;

/**
 * Each MCP tool is implemented as a handler bean.
 * The dispatcher discovers all handlers via CDI and routes by tool name.
 */
public interface McpToolHandler {

    String toolName();

    String description();

    Map<String, Object> inputSchema();

    ToolCallResult handle(JsonNode args);
}
