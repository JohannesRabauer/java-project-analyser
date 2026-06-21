package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.model.ClassGraph;
import dev.analyser.domain.model.ClassGraph.EdgeKind;
import dev.analyser.domain.model.ClassSummary;
import dev.analyser.domain.model.ClassSummary.ClassKind;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class GetClassDiagramHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;

    public GetClassDiagramHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String toolName() { return "get_class_diagram"; }

    @Override
    public String description() {
        return "Generates a PlantUML class diagram for the specified package from the dependency graph. Instant response.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job"),
                        "packageName", Map.of("type", "string", "description", "Package name to generate diagram for (e.g. com.example.service)")),
                "required", List.of("jobId", "packageName"));
    }

    @Override
    public ToolCallResult handle(JsonNode args) {
        if (args == null || !args.has("jobId") || !args.has("packageName")) {
            return ToolCallResult.error("Missing required parameters: jobId, packageName");
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(args.get("jobId").asText());
        } catch (IllegalArgumentException e) {
            return ToolCallResult.error("Invalid jobId format");
        }

        String packageName = args.get("packageName").asText().trim();

        var graphOpt = pipelineService.getClassGraph(jobId);
        if (graphOpt.isEmpty()) {
            return ToolCallResult.error("Analysis not completed for job " + jobId + ". Run analyse_project first and wait for completion.");
        }

        ClassGraph graph = graphOpt.get();
        Set<String> packageClasses = graph.classNames().stream()
                .filter(name -> {
                    int lastDot = name.lastIndexOf('.');
                    String pkg = lastDot > 0 ? name.substring(0, lastDot) : "";
                    return pkg.equals(packageName);
                })
                .collect(Collectors.toSet());

        if (packageClasses.isEmpty()) {
            return ToolCallResult.error("No classes found in package: " + packageName);
        }

        var sb = new StringBuilder("@startuml\n");
        // Declare classes
        for (String className : packageClasses) {
            graph.getClass(className).ifPresent(cs -> {
                String keyword = cs.kind() == ClassKind.INTERFACE ? "interface" : "class";
                sb.append(keyword).append(" ").append(cs.className()).append("\n");
            });
        }
        sb.append("\n");
        // Relationships
        for (var edge : graph.edges()) {
            if (packageClasses.contains(edge.source()) && packageClasses.contains(edge.target())) {
                String src = simpleName(edge.source());
                String tgt = simpleName(edge.target());
                String arrow = switch (edge.kind()) {
                    case EXTENDS -> " --|> ";
                    case IMPLEMENTS -> " ..|> ";
                    case DEPENDS_ON -> " --> ";
                };
                sb.append(src).append(arrow).append(tgt).append("\n");
            }
        }
        sb.append("@enduml");

        return ToolCallResult.success(sb.toString());
    }

    private String simpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }
}
