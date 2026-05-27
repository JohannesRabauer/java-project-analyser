package dev.analyser.adapter.in.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.in.mcp.McpProtocol.*;
import dev.analyser.adapter.out.persistence.RagRepository;
import dev.analyser.domain.model.*;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class McpDispatcher {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalysisPipelineService pipelineService;
    private final RagRepository ragRepository;

    public McpDispatcher(AnalysisPipelineService pipelineService, RagRepository ragRepository) {
        this.pipelineService = pipelineService;
        this.ragRepository = ragRepository;
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
            return String.format("{\"jsonrpc\":\"2.0\",\"id\":%s,\"error\":{\"code\":-32603,\"message\":\"Internal error: %s\"}}", id, e.getMessage());
        }
    }

    private McpResponse processRequest(McpRequest request) {
        String method = request.method();
        if (method == null) {
            return McpResponse.error(request.id(), -32600, "Invalid Request: missing method");
        }

        return switch (method) {
            case "initialize" -> handleInitialize(request);
            case "notifications/initialized" -> handleInitializedNotification(request);
            case "tools/list" -> handleToolsList(request);
            case "tools/call" -> handleToolsCall(request);
            case "resources/list" -> handleResourcesList(request);
            case "prompts/list" -> handlePromptsList(request);
            default -> McpResponse.error(request.id(), -32601, "Method not found: " + method);
        };
    }

    private McpResponse handleInitialize(McpRequest request) {
        ServerInfo info = new ServerInfo("Java-Project-Analyser-MCP", "1.0.0");
        ServerCapabilities capabilities = new ServerCapabilities(
                Map.of(), // tools capability
                Map.of(), // resources capability
                Map.of()  // prompts capability
        );
        InitializeResult result = new InitializeResult(McpProtocol.PROTOCOL_VERSION, capabilities, info);
        return McpResponse.success(request.id(), result);
    }

    private McpResponse handleInitializedNotification(McpRequest request) {
        // No-op for initialized notification
        return McpResponse.success(request.id(), Map.of());
    }

    private McpResponse handleToolsList(McpRequest request) {
        List<McpTool> tools = List.of(
                new McpTool(
                        "analyse_project",
                        "Ingests a Java project from the local filesystem or a Git URL and starts the 10-phase analysis and RAG indexing pipeline.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectPath", Map.of("type", "string", "description", "The absolute path of the Java project on the filesystem"),
                                        "gitUrl", Map.of("type", "string", "description", "Optional Git URL to clone the repository from")
                                ),
                                "required", List.of("projectPath")
                        )
                ),
                new McpTool(
                        "get_analysis_status",
                        "Retrieves the real-time status of a running or completed analysis job.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "jobId", Map.of("type", "string", "description", "The unique UUID of the analysis job")
                                ),
                                "required", List.of("jobId")
                        )
                ),
                new McpTool(
                        "search_codebase",
                        "Queries the indexed knowledge base of a project using vector-based similarity search to locate relevant code chunks.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "jobId", Map.of("type", "string", "description", "The unique UUID of the analysis job"),
                                        "query", Map.of("type", "string", "description", "The search query or description of what to find in the codebase")
                                ),
                                "required", List.of("jobId", "query")
                        )
                ),
                new McpTool(
                        "get_project_summary",
                        "Gets the high-level business purpose and classification of the project generated during Phase 1.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "jobId", Map.of("type", "string", "description", "The unique UUID of the analysis job")
                                ),
                                "required", List.of("jobId")
                        )
                ),
                new McpTool(
                        "get_ascii_report",
                        "Gets the complete compiled AsciiDoc architecture and design report for the analyzed project.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "jobId", Map.of("type", "string", "description", "The unique UUID of the analysis job")
                                ),
                                "required", List.of("jobId")
                        )
                )
        );

        return McpResponse.success(request.id(), new ToolsListResult(tools, null));
    }

    private McpResponse handleToolsCall(McpRequest request) {
        JsonNode params = request.params();
        if (params == null || !params.has("name")) {
            return McpResponse.error(request.id(), -32602, "Invalid params for tools/call");
        }

        String toolName = params.get("name").asText();
        JsonNode args = params.get("arguments");

        try {
            return switch (toolName) {
                case "analyse_project" -> callAnalyseProject(request.id(), args);
                case "get_analysis_status" -> callGetAnalysisStatus(request.id(), args);
                case "search_codebase" -> callSearchCodebase(request.id(), args);
                case "get_project_summary" -> callGetProjectSummary(request.id(), args);
                case "get_ascii_report" -> callGetAsciiReport(request.id(), args);
                default -> McpResponse.error(request.id(), -32601, "Tool not found: " + toolName);
            };
        } catch (Exception e) {
            return McpResponse.success(request.id(), ToolCallResult.error("Exception during tool call: " + e.getMessage()));
        }
    }

    private McpResponse callAnalyseProject(JsonNode id, JsonNode args) {
        if (args == null || !args.has("projectPath")) {
            return McpResponse.success(id, ToolCallResult.error("Missing required parameter: projectPath"));
        }

        String projectPath = args.get("projectPath").asText();
        UUID jobId = UUID.randomUUID();
        ProjectSource source = new LocalSource(Path.of(projectPath));

        pipelineService.startAnalysis(jobId, source);

        return McpResponse.success(id, ToolCallResult.success(
                String.format("Successfully started analysis for path: %s\nJob ID: %s\nUse the get_analysis_status tool to track progress.", projectPath, jobId)
        ));
    }

    private McpResponse callGetAnalysisStatus(JsonNode id, JsonNode args) {
        if (args == null || !args.has("jobId")) {
            return McpResponse.success(id, ToolCallResult.error("Missing required parameter: jobId"));
        }

        UUID jobId = UUID.fromString(args.get("jobId").asText());
        Optional<AnalysisJob> optJob = pipelineService.getJob(jobId);

        if (optJob.isEmpty()) {
            return McpResponse.success(id, ToolCallResult.error("No analysis job found with ID: " + jobId));
        }

        AnalysisJob job = optJob.get();
        List<PhaseResult> phases = pipelineService.getPhaseResults(jobId);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Job Status: %s\n", job.status()));
        sb.append(String.format("Created At: %s\n", job.createdAt()));
        sb.append(String.format("Updated At: %s\n", job.updatedAt()));
        sb.append("\nExecuted Phases:\n");

        for (int i = 1; i <= 9; i++) {
            int phaseId = i;
            Optional<PhaseResult> optPhase = phases.stream().filter(p -> p.phaseId() == phaseId).findFirst();
            if (optPhase.isPresent()) {
                sb.append(String.format("- Phase %d: %s (Completed: %s)\n", i, optPhase.get().status(), optPhase.get().completedAt()));
            } else {
                sb.append(String.format("- Phase %d: PENDING\n", i));
            }
        }

        return McpResponse.success(id, ToolCallResult.success(sb.toString()));
    }

    private McpResponse callSearchCodebase(JsonNode id, JsonNode args) {
        if (args == null || !args.has("jobId") || !args.has("query")) {
            return McpResponse.success(id, ToolCallResult.error("Missing required parameters: jobId, query"));
        }

        UUID jobId = UUID.fromString(args.get("jobId").asText());
        String query = args.get("query").asText();

        // Generate query embedding deterministically matching our pipeline
        double[] queryEmbedding = pipelineService.generateMockEmbedding(query);
        var results = ragRepository.search(jobId, queryEmbedding, 5);

        if (results.isEmpty()) {
            return McpResponse.success(id, ToolCallResult.success("No relevant code chunks found for query: " + query));
        }

        StringBuilder sb = new StringBuilder("Search Results (Vector similarity):\n\n");
        for (var res : results) {
            sb.append(String.format("--- Score: %.4f ---\n%s\n\n", res.similarity(), res.chunk().content()));
        }

        return McpResponse.success(id, ToolCallResult.success(sb.toString()));
    }

    private McpResponse callGetProjectSummary(JsonNode id, JsonNode args) {
        if (args == null || !args.has("jobId")) {
            return McpResponse.success(id, ToolCallResult.error("Missing required parameter: jobId"));
        }

        UUID jobId = UUID.fromString(args.get("jobId").asText());
        List<PhaseResult> phases = pipelineService.getPhaseResults(jobId);
        Optional<PhaseResult> optPhase1 = phases.stream().filter(p -> p.phaseId() == 1).findFirst();

        if (optPhase1.isEmpty()) {
            return McpResponse.success(id, ToolCallResult.error("Phase 1 result (Project Summary) not available yet. Current job status: " +
                    pipelineService.getJob(jobId).map(AnalysisJob::status).orElse(AnalysisStatus.FAILED)));
        }

        return McpResponse.success(id, ToolCallResult.success(optPhase1.get().resultJson()));
    }

    private McpResponse callGetAsciiReport(JsonNode id, JsonNode args) {
        if (args == null || !args.has("jobId")) {
            return McpResponse.success(id, ToolCallResult.error("Missing required parameter: jobId"));
        }

        UUID jobId = UUID.fromString(args.get("jobId").asText());
        String report = pipelineService.getAsciiReport(jobId);

        return McpResponse.success(id, ToolCallResult.success(report));
    }

    private McpResponse handleResourcesList(McpRequest request) {
        return McpResponse.success(request.id(), Map.of("resources", List.of()));
    }

    private McpResponse handlePromptsList(McpRequest request) {
        return McpResponse.success(request.id(), Map.of("prompts", List.of()));
    }
}
