package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class GetArchitectureOverviewHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;

    public GetArchitectureOverviewHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String toolName() { return "get_architecture_overview"; }

    @Override
    public String description() {
        return "Pre-computed architectural style, patterns, metrics, and risk areas. Instant response.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job")),
                "required", List.of("jobId"));
    }

    @Override
    public ToolCallResult handle(JsonNode args) {
        if (args == null || !args.has("jobId")) {
            return ToolCallResult.error("Missing required parameter: jobId");
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(args.get("jobId").asText());
        } catch (IllegalArgumentException e) {
            return ToolCallResult.error("Invalid jobId format");
        }

        var phase7 = pipelineService.getPhaseResult(jobId, 7);
        if (phase7.isEmpty()) {
            return ToolCallResult.error("Analysis not completed for job " + jobId + ". Run analyse_project first and wait for completion.");
        }

        var phase3 = pipelineService.getPhaseResult(jobId, 3);
        var sb = new StringBuilder();
        sb.append(phase7.get());
        if (phase3.isPresent()) {
            sb.append("\n\n--- Metrics ---\n").append(phase3.get());
        }

        return ToolCallResult.success(sb.toString());
    }
}
