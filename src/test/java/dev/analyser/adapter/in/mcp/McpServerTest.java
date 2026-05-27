package dev.analyser.adapter.in.mcp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import dev.analyser.domain.model.AnalysisJob;
import dev.analyser.domain.model.LocalSource;
import dev.analyser.domain.model.PhaseResult;
import dev.analyser.domain.model.PhaseStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class McpServerTest {

    @Inject
    McpDispatcher dispatcher;

    @Inject
    AnalysisJobRepository analysisJobRepository;

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
        assertTrue(tools.size() >= 6);

        // Verify tools exist
        boolean hasAnalyse = false;
        boolean hasSearch = false;
        boolean hasFindBugs = false;
        for (var tool : tools) {
            String name = tool.get("name").asText();
            if ("analyse_project".equals(name)) hasAnalyse = true;
            if ("search_codebase".equals(name)) hasSearch = true;
            if ("find_bugs".equals(name)) hasFindBugs = true;
        }

        assertTrue(hasAnalyse, "analyse_project tool missing");
        assertTrue(hasSearch, "search_codebase tool missing");
        assertTrue(hasFindBugs, "find_bugs tool missing");
    }

    @Test
    public void testResourcesList() throws Exception {
        String request = """
        {
            "jsonrpc": "2.0",
            "id": "list_resources_id",
            "method": "resources/list",
            "params": {}
        }
        """;

        String responseStr = dispatcher.dispatch(request);
        var root = mapper.readTree(responseStr);

        assertEquals("list_resources_id", root.get("id").asText());
        assertTrue(root.get("result").get("resources").isArray());

        var resources = root.get("result").get("resources");
        assertTrue(resources.toString().contains("mcp://analysis/jobs"));
        assertTrue(resources.toString().contains("mcp://analysis/job/{jobId}/summary"));
        assertTrue(resources.toString().contains("mcp://analysis/job/{jobId}/structure"));
        assertTrue(resources.toString().contains("mcp://analysis/job/{jobId}/report"));
    }

    @Test
    public void testPromptsList() throws Exception {
        String request = """
        {
            "jsonrpc": "2.0",
            "id": "list_prompts_id",
            "method": "prompts/list",
            "params": {}
        }
        """;

        String responseStr = dispatcher.dispatch(request);
        var root = mapper.readTree(responseStr);

        assertEquals("list_prompts_id", root.get("id").asText());
        assertTrue(root.get("result").get("prompts").isArray());
        assertTrue(root.get("result").get("prompts").toString().contains("review-codebase-risks"));
        assertTrue(root.get("result").get("prompts").toString().contains("suggest-feature-impl"));
    }

    @Test
    public void testResourceReadSummary() throws Exception {
        UUID jobId = seedJobWithPhaseResult(1, "{\"executiveSummary\":\"summary\"}");

        String request = """
        {
            "jsonrpc": "2.0",
            "id": "read_summary_id",
            "method": "resources/read",
            "params": {
                "uri": "mcp://analysis/job/%s/summary"
            }
        }
        """.formatted(jobId);

        String responseStr = dispatcher.dispatch(request);
        var root = mapper.readTree(responseStr);

        assertEquals("read_summary_id", root.get("id").asText());
        assertTrue(root.get("result").toString().contains("summary"));
    }

    @Test
    public void testFindBugsToolUsesRecordedRiskPhase() throws Exception {
        UUID jobId = seedJobWithPhaseResult(7, """
                {
                  "risks": [
                    {
                      "category": "Resilience",
                      "severity": "Medium",
                      "description": "Raw stack-trace print can expose implementation details."
                    }
                  ]
                }
                """);

        String request = """
        {
            "jsonrpc": "2.0",
            "id": "find_bugs_id",
            "method": "tools/call",
            "params": {
                "name": "find_bugs",
                "arguments": {
                    "jobId": "%s",
                    "context": "look for resilience issues"
                }
            }
        }
        """.formatted(jobId);

        String responseStr = dispatcher.dispatch(request);
        var root = mapper.readTree(responseStr);

        assertTrue(root.get("result").toString().contains("Resilience"));
        assertTrue(root.get("result").toString().contains("Raw stack-trace print"));
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

    private UUID seedJobWithPhaseResult(int phaseId, String resultJson) {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();
        analysisJobRepository.create(AnalysisJob.create(jobId, new LocalSource(Path.of("/workspace/project")), now));
        analysisJobRepository.savePhaseResult(new PhaseResult(jobId, phaseId, PhaseStatus.COMPLETED, resultJson, now));
        return jobId;
    }
}
