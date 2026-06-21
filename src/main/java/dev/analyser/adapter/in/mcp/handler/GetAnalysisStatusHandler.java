package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.model.AnalysisJob;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class GetAnalysisStatusHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;

    public GetAnalysisStatusHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String toolName() { return "get_analysis_status"; }

    @Override
    public String description() {
        return "Returns the current status and progress of an analysis job. Lightweight, instant response.";
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

        return pipelineService.getJob(jobId)
                .map(this::formatStatus)
                .orElse(ToolCallResult.error("No analysis job found with ID: " + jobId));
    }

    private ToolCallResult formatStatus(AnalysisJob job) {
        var phases = pipelineService.getPhaseResults(job.id());
        int completed = phases.size();
        int total = 8;
        int percentage = job.status().name().equals("COMPLETED") || job.status().name().equals("INDEXED")
                ? 100 : (completed * 100 / total);

        return ToolCallResult.success(String.format(
                "Status: %s\nPhases completed: %d/%d\nProgress: %d%%\nCreated: %s",
                job.status(), completed, total, percentage, job.createdAt()));
    }
}
