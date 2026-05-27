package dev.analyser.adapter.in.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.in.mcp.McpProtocol.*;
import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import dev.analyser.adapter.out.persistence.RagRepository;
import dev.analyser.domain.model.*;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class McpDispatcher {

    private static final Pattern JOB_RESOURCE_PATTERN =
            Pattern.compile("^mcp://analysis/job/([0-9a-fA-F-]+)/([a-z]+)$");

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalysisPipelineService pipelineService;
    private final AnalysisJobRepository analysisJobRepository;
    private final RagRepository ragRepository;

    public McpDispatcher(
            AnalysisPipelineService pipelineService,
            AnalysisJobRepository analysisJobRepository,
            RagRepository ragRepository) {
        this.pipelineService = pipelineService;
        this.analysisJobRepository = analysisJobRepository;
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
        if (!"2.0".equals(request.jsonrpc())) {
            return McpResponse.error(request.id(), -32600, "Invalid Request: jsonrpc must be 2.0");
        }

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
            case "resources/read" -> handleResourcesRead(request);
            case "prompts/list" -> handlePromptsList(request);
            case "prompts/get" -> handlePromptsGet(request);
            default -> McpResponse.error(request.id(), -32601, "Method not found: " + method);
        };
    }

    private McpResponse handleInitialize(McpRequest request) {
        ServerInfo info = new ServerInfo("Java-Project-Analyser-MCP", "1.0.0");
        ServerCapabilities capabilities = new ServerCapabilities(
                Map.of("listChanged", true),
                Map.of("listChanged", true, "subscribe", true),
                Map.of("listChanged", true)
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
                        "find_bugs",
                        "Reviews recorded risk findings and relevant indexed code chunks to identify likely bugs, code smells, and testing gaps.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "jobId", Map.of("type", "string", "description", "The unique UUID of the analysis job"),
                                        "context", Map.of("type", "string", "description", "What kind of bug or quality issue to look for")
                                ),
                                "required", List.of("jobId", "context")
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
                case "find_bugs" -> callFindBugs(request.id(), args);
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
        ProjectSource source = selectProjectSource(args, projectPath);

        pipelineService.startAnalysis(jobId, source);

        return McpResponse.success(id, ToolCallResult.success(
                String.format("Successfully started analysis for path: %s\nJob ID: %s\nUse the get_analysis_status tool to track progress.", projectPath, jobId)
        ));
    }

    private ProjectSource selectProjectSource(JsonNode args, String projectPath) {
        if (args.hasNonNull("gitUrl")) {
            var gitUrl = args.get("gitUrl").asText().trim();
            if (!gitUrl.isEmpty()) {
                return new GitSource(URI.create(gitUrl));
            }
        }

        return new LocalSource(Path.of(projectPath));
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
        int completedPhases = phases.size();
        int currentPhase = determineCurrentPhase(job.status(), completedPhases);
        int progressPercentage = determineProgressPercentage(job.status(), completedPhases);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Job Status: %s\n", job.status()));
        sb.append(String.format("Current Phase: %d\n", currentPhase));
        sb.append(String.format("Progress Percentage: %d\n", progressPercentage));
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

    private McpResponse callFindBugs(JsonNode id, JsonNode args) {
        if (args == null || !args.has("jobId") || !args.has("context")) {
            return McpResponse.success(id, ToolCallResult.error("Missing required parameters: jobId, context"));
        }

        UUID jobId = UUID.fromString(args.get("jobId").asText());
        String context = args.get("context").asText();
        Optional<AnalysisJob> optJob = pipelineService.getJob(jobId);
        if (optJob.isEmpty()) {
            return McpResponse.success(id, ToolCallResult.error("No analysis job found with ID: " + jobId));
        }

        List<PhaseResult> phases = pipelineService.getPhaseResults(jobId);
        Optional<PhaseResult> riskPhase = phases.stream().filter(phase -> phase.phaseId() == 7).findFirst();

        StringBuilder sb = new StringBuilder();
        sb.append("Bug Findings\n");
        sb.append("Context: ").append(context).append("\n\n");

        riskPhase.ifPresentOrElse(
                phase -> sb.append("Recorded Risk Analysis:\n").append(phase.resultJson()).append("\n\n"),
                () -> sb.append("Recorded Risk Analysis:\nNo phase 7 risk data available.\n\n"));

        double[] queryEmbedding = pipelineService.generateMockEmbedding(context);
        var results = ragRepository.search(jobId, queryEmbedding, 3);
        if (!results.isEmpty()) {
            sb.append("Relevant Indexed Code Chunks:\n");
            for (var result : results) {
                sb.append(String.format("- Score %.4f%n", result.similarity()));
                sb.append(result.chunk().content()).append("\n");
            }
        }

        return McpResponse.success(id, ToolCallResult.success(sb.toString().trim()));
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
        List<McpResource> resources = List.of(
                new McpResource(
                        "mcp://analysis/jobs",
                        "Analysis jobs",
                        "Lists all analysis jobs with their project paths and statuses.",
                        "application/json"),
                new McpResource(
                        "mcp://analysis/job/{jobId}/summary",
                        "Analysis summary",
                        "Returns the phase 1 summary for a specific analysis job.",
                        "application/json"),
                new McpResource(
                        "mcp://analysis/job/{jobId}/structure",
                        "Analysis structure",
                        "Returns the phase 2 project structure result for a specific analysis job.",
                        "application/json"),
                new McpResource(
                        "mcp://analysis/job/{jobId}/report",
                        "AsciiDoc report",
                        "Returns the compiled AsciiDoc report for a specific analysis job.",
                        "text/plain"));
        return McpResponse.success(request.id(), new ResourcesListResult(resources, null));
    }

    private McpResponse handlePromptsList(McpRequest request) {
        return McpResponse.success(request.id(), new PromptsListResult(List.of(
                new McpPrompt(
                        "review-codebase-risks",
                        "Guides an MCP client through querying the analysed codebase and compiling an architectural risk report."),
                new McpPrompt(
                        "suggest-feature-impl",
                        "Guides an MCP client through retrieving architecture context before proposing feature implementation advice.")),
                null));
    }

    private McpResponse handleResourcesRead(McpRequest request) {
        JsonNode params = request.params();
        if (params == null || !params.has("uri")) {
            return McpResponse.error(request.id(), -32602, "Invalid params for resources/read");
        }

        String uri = params.get("uri").asText();
        if ("mcp://analysis/jobs".equals(uri)) {
            String jobsJson = serialiseJobs();
            return McpResponse.success(
                    request.id(),
                    new ResourcesReadResult(List.of(new ResourceContents(uri, "application/json", jobsJson))));
        }

        Matcher matcher = JOB_RESOURCE_PATTERN.matcher(uri);
        if (!matcher.matches()) {
            return McpResponse.error(request.id(), -32602, "Unsupported resource URI: " + uri);
        }

        UUID jobId = UUID.fromString(matcher.group(1));
        String resourceType = matcher.group(2);
        List<PhaseResult> phases = pipelineService.getPhaseResults(jobId);

        return switch (resourceType) {
            case "summary" -> readPhaseResource(request.id(), uri, phases, 1);
            case "structure" -> readPhaseResource(request.id(), uri, phases, 2);
            case "report" -> McpResponse.success(
                    request.id(),
                    new ResourcesReadResult(List.of(new ResourceContents(
                            uri,
                            "text/plain",
                            pipelineService.getAsciiReport(jobId)))));
            default -> McpResponse.error(request.id(), -32602, "Unsupported resource URI: " + uri);
        };
    }

    private McpResponse handlePromptsGet(McpRequest request) {
        JsonNode params = request.params();
        if (params == null || !params.has("name")) {
            return McpResponse.error(request.id(), -32602, "Invalid params for prompts/get");
        }

        String promptName = params.get("name").asText();
        PromptGetResult result = switch (promptName) {
            case "review-codebase-risks" -> new PromptGetResult(
                    "Use the MCP tools to inspect the analysed codebase and assemble a risk report grounded in retrieved facts.",
                    List.of(
                            new PromptMessage("system", "You are reviewing architectural and implementation risks in an analysed Java codebase."),
                            new PromptMessage("user", "Inspect the available MCP resources, check the project summary, search for the relevant hotspots, and produce a concise risk report grouped by severity.")));
            case "suggest-feature-impl" -> new PromptGetResult(
                    "Use the MCP tools to retrieve architecture and class context before suggesting a feature implementation plan.",
                    List.of(
                            new PromptMessage("system", "You help an AI client propose feature implementation guidance grounded in retrieved architecture context."),
                            new PromptMessage("user", "Read the available summary and structure resources, search the indexed codebase for relevant classes, and then propose how to add the requested feature without violating the existing design.")));
            default -> null;
        };

        if (result == null) {
            return McpResponse.error(request.id(), -32602, "Unknown prompt: " + promptName);
        }
        return McpResponse.success(request.id(), result);
    }

    private int determineCurrentPhase(AnalysisStatus status, int completedPhases) {
        return switch (status) {
            case PENDING -> 1;
            case RUNNING -> Math.min(completedPhases + 1, 9);
            case COMPLETED -> 9;
            case INDEXED -> 10;
            case FAILED -> Math.max(1, completedPhases);
        };
    }

    private int determineProgressPercentage(AnalysisStatus status, int completedPhases) {
        return switch (status) {
            case PENDING -> 0;
            case RUNNING, FAILED -> Math.min(completedPhases * 10, 90);
            case COMPLETED -> 90;
            case INDEXED -> 100;
        };
    }

    private McpResponse readPhaseResource(JsonNode id, String uri, List<PhaseResult> phases, int phaseId) {
        Optional<PhaseResult> phaseResult = phases.stream().filter(phase -> phase.phaseId() == phaseId).findFirst();
        if (phaseResult.isEmpty()) {
            return McpResponse.error(id, -32602, "Requested resource is not available yet: " + uri);
        }

        return McpResponse.success(
                id,
                new ResourcesReadResult(List.of(new ResourceContents(uri, "application/json", phaseResult.get().resultJson()))));
    }

    private String serialiseJobs() {
        try {
            return mapper.writeValueAsString(analysisJobRepository.findAll().stream()
                    .map(job -> Map.of(
                            "jobId", job.id().toString(),
                            "status", job.status().name(),
                            "projectPath", describeProjectSource(job.source()),
                            "createdAt", job.createdAt().toString(),
                            "updatedAt", job.updatedAt().toString()))
                    .toList());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialise analysis jobs", exception);
        }
    }

    private String describeProjectSource(ProjectSource source) {
        if (source instanceof LocalSource localSource) {
            return localSource.rootPath().toString();
        }
        if (source instanceof GitSource gitSource) {
            return gitSource.repositoryUrl().toString();
        }
        return source.toString();
    }
}
