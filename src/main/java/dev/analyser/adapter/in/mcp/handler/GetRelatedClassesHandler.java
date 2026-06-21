package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.model.ClassGraph;
import dev.analyser.domain.model.ClassGraph.RelatedClasses;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class GetRelatedClassesHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;

    public GetRelatedClassesHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String toolName() { return "get_related_classes"; }

    @Override
    public String description() {
        return "Returns dependency graph relationships for a class: what it depends on, what depends on it, inheritance chain. Pre-computed from AST — instant response.";
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

        var graphOpt = pipelineService.getClassGraph(jobId);
        if (graphOpt.isEmpty()) {
            return ToolCallResult.error("Analysis not completed for job " + jobId + ". Run analyse_project first and wait for completion.");
        }

        ClassGraph graph = graphOpt.get();

        // Try exact match first, then try to find by simple name
        String resolvedName = graph.classNames().contains(className)
                ? className
                : graph.classNames().stream()
                        .filter(n -> n.endsWith("." + className))
                        .findFirst()
                        .orElse(null);

        if (resolvedName == null) {
            return ToolCallResult.error("Class not found in analysis: " + className
                    + ". Available classes: " + String.join(", ", graph.classNames().stream().limit(20).toList()));
        }

        RelatedClasses related = graph.relatedTo(resolvedName);
        return ToolCallResult.success(formatRelated(related));
    }

    private String formatRelated(RelatedClasses r) {
        var sb = new StringBuilder();
        sb.append("Class: ").append(r.className()).append("\n\n");

        if (r.superClass() != null) {
            sb.append("Extends: ").append(r.superClass()).append("\n");
        }
        if (!r.implementsInterfaces().isEmpty()) {
            sb.append("Implements: ").append(String.join(", ", r.implementsInterfaces())).append("\n");
        }
        if (!r.dependsOn().isEmpty()) {
            sb.append("\nDepends on:\n");
            r.dependsOn().forEach(d -> sb.append("  - ").append(d).append("\n"));
        }
        if (!r.dependedOnBy().isEmpty()) {
            sb.append("\nDepended on by:\n");
            r.dependedOnBy().forEach(d -> sb.append("  - ").append(d).append("\n"));
        }
        if (!r.extendedBy().isEmpty()) {
            sb.append("\nExtended by:\n");
            r.extendedBy().forEach(d -> sb.append("  - ").append(d).append("\n"));
        }
        if (!r.implementedBy().isEmpty()) {
            sb.append("\nImplemented by:\n");
            r.implementedBy().forEach(d -> sb.append("  - ").append(d).append("\n"));
        }

        return sb.toString().trim();
    }
}
