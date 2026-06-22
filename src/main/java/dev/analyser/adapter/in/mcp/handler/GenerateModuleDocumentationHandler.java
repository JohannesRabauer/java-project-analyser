package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.application.port.out.LlmPort;
import dev.analyser.domain.model.ClassSummary;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class GenerateModuleDocumentationHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;
    private final LlmPort queryLlm;

    public GenerateModuleDocumentationHandler(AnalysisPipelineService pipelineService, @Named("queryLlm") LlmPort queryLlm) {
        this.pipelineService = pipelineService;
        this.queryLlm = queryLlm;
    }

    @Override public String toolName() { return "generate_module_documentation"; }

    @Override public String description() {
        return "Generates README-style documentation for a specific package/module, including purpose, public API, usage examples, and dependencies. Heavy — 10-20 seconds.";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job"),
                        "packageName", Map.of("type", "string", "description", "The package to document (e.g. com.example.service)")),
                "required", List.of("jobId", "packageName"));
    }

    @Override
    public ToolCallResult handle(JsonNode args) {
        if (args == null || !args.has("jobId") || !args.has("packageName"))
            return ToolCallResult.error("Missing required parameters: jobId, packageName");

        UUID jobId;
        try { jobId = UUID.fromString(args.get("jobId").asText()); }
        catch (IllegalArgumentException e) { return ToolCallResult.error("Invalid jobId format"); }

        String packageName = args.get("packageName").asText().trim();
        var allClasses = pipelineService.getClasses(jobId);
        if (allClasses.isEmpty()) return ToolCallResult.error("Analysis not completed. Run analyse_project first.");
        if (queryLlm == null) return ToolCallResult.error("LLM not configured. Set QUERY_MODEL_PROVIDER.");

        // Filter classes in the target package (exact match + sub-packages)
        var moduleClasses = allClasses.stream()
                .filter(c -> c.packageName().equals(packageName) || c.packageName().startsWith(packageName + "."))
                .toList();

        if (moduleClasses.isEmpty())
            return ToolCallResult.error("No classes found in package: " + packageName);

        // Build context
        var graph = pipelineService.getClassGraph(jobId);
        var classDetails = moduleClasses.stream()
                .map(c -> {
                    var related = graph.map(g -> g.relatedTo(c.qualifiedName())).orElse(null);
                    return c.qualifiedName() + " (" + c.kind() + ", " + c.methods().size() + " methods)"
                            + (c.superClass() != null ? " extends " + c.superClass() : "")
                            + (!c.interfaces().isEmpty() ? " implements " + String.join(", ", c.interfaces()) : "")
                            + "\n  Public methods: " + c.methods().stream().filter(m -> m.isPublic())
                                .map(m -> m.returnType() + " " + m.name() + "(" + String.join(", ", m.parameterTypes()) + ")")
                                .collect(Collectors.joining(", "))
                            + (related != null && !related.dependedOnBy().isEmpty() ?
                                "\n  Used by: " + String.join(", ", related.dependedOnBy().stream().limit(5).toList()) : "");
                })
                .collect(Collectors.joining("\n\n"));

        String response = queryLlm.prompt(
                """
                You are a technical writer creating module documentation in Markdown format.
                Write clear, specific documentation based on the actual classes and methods provided.
                
                Structure:
                # Module: {package name}
                
                ## Purpose
                One paragraph explaining what this module does and why it exists.
                
                ## Public API
                List the key classes and their most important public methods with brief descriptions.
                
                ## Dependencies
                What this module depends on (other packages/external libraries).
                
                ## Usage
                Brief code example showing how to use the main class(es) in this module.
                
                ## Design Decisions
                Any notable patterns or architectural choices visible in the code.
                """,
                "Generate documentation for package: " + packageName + "\n\nClasses in this module:\n\n" + classDetails);

        return ToolCallResult.success(response);
    }
}
