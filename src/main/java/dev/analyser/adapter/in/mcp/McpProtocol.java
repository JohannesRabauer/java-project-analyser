package dev.analyser.adapter.in.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public class McpProtocol {

    public static final String PROTOCOL_VERSION = "2024-11-05";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpRequest(
            String jsonrpc,
            JsonNode id,
            String method,
            JsonNode params
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpResponse(
            String jsonrpc,
            JsonNode id,
            Object result,
            McpError error
    ) {
        public static McpResponse success(JsonNode id, Object result) {
            return new McpResponse("2.0", id, result, null);
        }

        public static McpResponse error(JsonNode id, int code, String message) {
            return new McpResponse("2.0", id, null, new McpError(code, message, null));
        }

        public static McpResponse error(JsonNode id, int code, String message, JsonNode data) {
            return new McpResponse("2.0", id, null, new McpError(code, message, data));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record McpError(
            int code,
            String message,
            JsonNode data
    ) {}

    public record ServerInfo(
            String name,
            String version
    ) {}

    public record ServerCapabilities(
            Map<String, Object> tools,
            Map<String, Object> resources,
            Map<String, Object> prompts
    ) {}

    public record InitializeResult(
            String protocolVersion,
            ServerCapabilities capabilities,
            ServerInfo serverInfo
    ) {}

    public record McpTool(
            String name,
            String description,
            Map<String, Object> inputSchema
    ) {}

    public record ToolsListResult(
            List<McpTool> tools,
            String nextCursor
    ) {}

    public record McpResource(
            String uri,
            String name,
            String description,
            String mimeType
    ) {}

    public record ResourcesListResult(
            List<McpResource> resources,
            String nextCursor
    ) {}

    public record ResourceContents(
            String uri,
            String mimeType,
            String text
    ) {}

    public record ResourcesReadResult(
            List<ResourceContents> contents
    ) {}

    public record McpPrompt(
            String name,
            String description
    ) {}

    public record PromptsListResult(
            List<McpPrompt> prompts,
            String nextCursor
    ) {}

    public record PromptMessage(
            String role,
            String content
    ) {}

    public record PromptGetResult(
            String description,
            List<PromptMessage> messages
    ) {}

    public record McpContent(
            String type,
            String text
    ) {
        public static McpContent text(String text) {
            return new McpContent("text", text);
        }
    }

    public record ToolCallResult(
            List<McpContent> content,
            Boolean isError
    ) {
        public static ToolCallResult success(String text) {
            return new ToolCallResult(List.of(McpContent.text(text)), null);
        }

        public static ToolCallResult error(String text) {
            return new ToolCallResult(List.of(McpContent.text(text)), true);
        }
    }
}
