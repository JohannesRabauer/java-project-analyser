package dev.analyser.adapter.in.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpDispatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void initializeReturnsProtocolVersion() throws Exception {
        var dispatcher = new McpDispatcher(List.of());
        var response = dispatch(dispatcher, """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""");

        assertThat(response.get("result").get("protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(response.get("result").get("serverInfo").get("name").asText()).isEqualTo("Java-Project-Analyser-MCP");
    }

    @Test
    void toolsListReturnsRegisteredHandlers() throws Exception {
        var handler = stubHandler("test_tool", "A test tool");
        var dispatcher = new McpDispatcher(List.of(handler));

        var response = dispatch(dispatcher, """
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""");

        var tools = response.get("result").get("tools");
        assertThat(tools.size()).isEqualTo(1);
        assertThat(tools.get(0).get("name").asText()).isEqualTo("test_tool");
        assertThat(tools.get(0).get("description").asText()).isEqualTo("A test tool");
    }

    @Test
    void toolsCallRoutesToCorrectHandler() throws Exception {
        var handler = stubHandler("my_tool", "desc");
        var dispatcher = new McpDispatcher(List.of(handler));

        var response = dispatch(dispatcher, """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"my_tool","arguments":{"key":"value"}}}""");

        assertThat(response.get("result").get("content").get(0).get("text").asText()).isEqualTo("handled");
    }

    @Test
    void toolsCallReturnsErrorForUnknownTool() throws Exception {
        var dispatcher = new McpDispatcher(List.of());

        var response = dispatch(dispatcher, """
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"unknown","arguments":{}}}""");

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-32601);
    }

    @Test
    void unknownMethodReturnsError() throws Exception {
        var dispatcher = new McpDispatcher(List.of());

        var response = dispatch(dispatcher, """
                {"jsonrpc":"2.0","id":5,"method":"nonexistent","params":{}}""");

        assertThat(response.get("error").get("code").asInt()).isEqualTo(-32601);
    }

    private JsonNode dispatch(McpDispatcher dispatcher, String json) throws Exception {
        return mapper.readTree(dispatcher.dispatch(json));
    }

    private McpToolHandler stubHandler(String name, String description) {
        return new McpToolHandler() {
            @Override public String toolName() { return name; }
            @Override public String description() { return description; }
            @Override public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
            @Override public ToolCallResult handle(JsonNode args) { return ToolCallResult.success("handled"); }
        };
    }
}
