package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class GetCheckstyleWarningsHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetCheckstyleWarningsHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String toolName() { return "get_checkstyle_warnings"; }

    @Override
    public String description() {
        return "Pre-computed code quality warnings and anti-patterns detected via static analysis. Instant response.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job"),
                        "scope", Map.of("type", "string", "description", "Optional: filter warnings by package or class name")),
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

        var result = pipelineService.getPhaseResult(jobId, 6);
        if (result.isEmpty()) {
            return ToolCallResult.error("Analysis not completed for job " + jobId + ". Run analyse_project first and wait for completion.");
        }

        String scope = args.has("scope") ? args.get("scope").asText().trim() : null;
        if (scope == null || scope.isEmpty()) {
            return ToolCallResult.success(result.get());
        }

        // Filter by scope
        try {
            JsonNode warnings = mapper.readTree(result.get());
            if (warnings.isArray()) {
                var filtered = new StringBuilder("[");
                boolean first = true;
                for (JsonNode warning : warnings) {
                    String text = warning.toString();
                    if (text.contains(scope)) {
                        if (!first) filtered.append(",");
                        filtered.append(text);
                        first = false;
                    }
                }
                filtered.append("]");
                return ToolCallResult.success(filtered.toString());
            }
        } catch (Exception e) {
            // Fall through to unfiltered
        }
        return ToolCallResult.success(result.get());
    }
}
