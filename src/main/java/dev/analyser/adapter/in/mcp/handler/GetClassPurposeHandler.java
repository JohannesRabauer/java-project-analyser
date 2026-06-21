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
public class GetClassPurposeHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetClassPurposeHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String toolName() { return "get_class_purpose"; }

    @Override
    public String description() {
        return "Pre-computed one-paragraph explanation of what a class does and why it exists. Instant response.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job"),
                        "className", Map.of("type", "string", "description", "Fully qualified class name (e.g. com.example.OrderService)")),
                "required", List.of("jobId", "className"));
    }

    @Override
    public ToolCallResult handle(JsonNode args) {
        if (args == null || !args.has("jobId") || !args.has("className")) {
            return ToolCallResult.error("Missing required parameters: jobId, className");
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(args.get("jobId").asText());
        } catch (IllegalArgumentException e) {
            return ToolCallResult.error("Invalid jobId format");
        }

        String className = args.get("className").asText().trim();

        var result = pipelineService.getPhaseResult(jobId, 5);
        if (result.isEmpty()) {
            return ToolCallResult.error("Analysis not completed for job " + jobId + ". Run analyse_project first and wait for completion.");
        }

        try {
            JsonNode phase5 = mapper.readTree(result.get());
            if (phase5.has(className)) {
                return ToolCallResult.success(phase5.get(className).asText());
            }
            // Try matching by simple name
            var fields = phase5.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getKey().endsWith("." + className)) {
                    return ToolCallResult.success(entry.getValue().asText());
                }
            }
            return ToolCallResult.error("Class not found in analysis: " + className);
        } catch (Exception e) {
            return ToolCallResult.success(result.get());
        }
    }
}
