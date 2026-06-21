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
public class GetModulePurposeHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetModulePurposeHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String toolName() { return "get_module_purpose"; }

    @Override
    public String description() {
        return "Pre-computed module/package responsibility summary. Instant response.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job"),
                        "moduleName", Map.of("type", "string", "description", "Module or package name (e.g. com.example.service)")),
                "required", List.of("jobId", "moduleName"));
    }

    @Override
    public ToolCallResult handle(JsonNode args) {
        if (args == null || !args.has("jobId") || !args.has("moduleName")) {
            return ToolCallResult.error("Missing required parameters: jobId, moduleName");
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(args.get("jobId").asText());
        } catch (IllegalArgumentException e) {
            return ToolCallResult.error("Invalid jobId format");
        }

        String moduleName = args.get("moduleName").asText().trim();

        // Try phase 4 for module info
        var phase4 = pipelineService.getPhaseResult(jobId, 4);
        if (phase4.isPresent()) {
            try {
                JsonNode summary = mapper.readTree(phase4.get());
                if (summary.has("modules")) {
                    JsonNode modules = summary.get("modules");
                    if (modules.has(moduleName)) {
                        return ToolCallResult.success(modules.get(moduleName).asText());
                    }
                    // Try partial match
                    var fields = modules.fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        if (entry.getKey().contains(moduleName) || moduleName.contains(entry.getKey())) {
                            return ToolCallResult.success(entry.getKey() + ": " + entry.getValue().asText());
                        }
                    }
                }
            } catch (Exception e) {
                // Fall through to class graph derivation
            }
        }

        // Derive from class graph + phase 5
        var graphOpt = pipelineService.getClassGraph(jobId);
        if (graphOpt.isEmpty()) {
            return ToolCallResult.error("Analysis not completed for job " + jobId + ". Run analyse_project first and wait for completion.");
        }

        var classesInModule = graphOpt.get().classNames().stream()
                .filter(name -> {
                    int lastDot = name.lastIndexOf('.');
                    String pkg = lastDot > 0 ? name.substring(0, lastDot) : "";
                    return pkg.equals(moduleName) || pkg.startsWith(moduleName + ".");
                })
                .toList();

        if (classesInModule.isEmpty()) {
            return ToolCallResult.error("No classes found in module: " + moduleName);
        }

        var sb = new StringBuilder("Module: ").append(moduleName).append("\nClasses:\n");
        var phase5 = pipelineService.getPhaseResult(jobId, 5);
        JsonNode purposes = null;
        if (phase5.isPresent()) {
            try { purposes = mapper.readTree(phase5.get()); } catch (Exception ignored) {}
        }

        for (String className : classesInModule) {
            sb.append("  - ").append(className);
            if (purposes != null && purposes.has(className)) {
                sb.append(": ").append(purposes.get(className).asText());
            }
            sb.append("\n");
        }

        return ToolCallResult.success(sb.toString().trim());
    }
}
