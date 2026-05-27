package dev.analyser.adapter.in.mcp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class McpServerTest {

    @Inject
    McpDispatcher dispatcher;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testInitializeHandshake() throws Exception {
        String request = """
        {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": { "name": "test-client", "version": "1.0.0" }
            }
        }
        """;

        String responseStr = dispatcher.dispatch(request);
        assertNotNull(responseStr);

        var root = mapper.readTree(responseStr);
        assertEquals("2.0", root.get("jsonrpc").asText());
        assertEquals(1, root.get("id").asInt());
        assertTrue(root.has("result"));
        assertEquals("2024-11-05", root.get("result").get("protocolVersion").asText());
        assertEquals("Java-Project-Analyser-MCP", root.get("result").get("serverInfo").get("name").asText());
    }

    @Test
    public void testToolsList() throws Exception {
        String request = """
        {
            "jsonrpc": "2.0",
            "id": "list_tools_id",
            "method": "tools/list",
            "params": {}
        }
        """;

        String responseStr = dispatcher.dispatch(request);
        assertNotNull(responseStr);

        var root = mapper.readTree(responseStr);
        assertEquals("list_tools_id", root.get("id").asText());
        assertTrue(root.has("result"));
        assertTrue(root.get("result").has("tools"));

        var tools = root.get("result").get("tools");
        assertTrue(tools.isArray());
        assertTrue(tools.size() >= 5);

        // Verify tools exist
        boolean hasAnalyse = false;
        boolean hasSearch = false;
        for (var tool : tools) {
            String name = tool.get("name").asText();
            if ("analyse_project".equals(name)) hasAnalyse = true;
            if ("search_codebase".equals(name)) hasSearch = true;
        }

        assertTrue(hasAnalyse, "analyse_project tool missing");
        assertTrue(hasSearch, "search_codebase tool missing");
    }

    @Test
    public void testInvalidMethod() throws Exception {
        String request = """
        {
            "jsonrpc": "2.0",
            "id": 99,
            "method": "non_existent_method"
        }
        """;

        String responseStr = dispatcher.dispatch(request);
        assertNotNull(responseStr);

        var root = mapper.readTree(responseStr);
        assertTrue(root.has("error"));
        assertEquals(-32601, root.get("error").get("code").asInt());
    }
}
