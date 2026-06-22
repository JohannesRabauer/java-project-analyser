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
public class GenerateArc42OverviewHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;
    private final LlmPort queryLlm;

    public GenerateArc42OverviewHandler(AnalysisPipelineService pipelineService, @Named("queryLlm") LlmPort queryLlm) {
        this.pipelineService = pipelineService;
        this.queryLlm = queryLlm;
    }

    @Override public String toolName() { return "generate_arc42_overview"; }

    @Override public String description() {
        return "Generates arc42 architecture documentation (chapters 1-5) from analyzed project data using LLM synthesis. Heavy — 20-40 seconds.";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of("jobId", Map.of("type", "string", "description", "The UUID of the analysis job")),
                "required", List.of("jobId"));
    }

    @Override
    public ToolCallResult handle(JsonNode args) {
        if (args == null || !args.has("jobId")) return ToolCallResult.error("Missing required parameter: jobId");
        UUID jobId;
        try { jobId = UUID.fromString(args.get("jobId").asText()); }
        catch (IllegalArgumentException e) { return ToolCallResult.error("Invalid jobId format"); }

        var classes = pipelineService.getClasses(jobId);
        if (classes.isEmpty()) return ToolCallResult.error("Analysis not completed. Run analyse_project first.");
        if (queryLlm == null) return ToolCallResult.error("LLM not configured. Set QUERY_MODEL_PROVIDER.");

        // Gather all context
        var phase2 = pipelineService.getPhaseResult(jobId, 2).orElse("{}");
        var phase4 = pipelineService.getPhaseResult(jobId, 4).orElse("{}");
        var phase7 = pipelineService.getPhaseResult(jobId, 7).orElse("{}");
        var graph = pipelineService.getClassGraph(jobId);

        var packages = classes.stream().map(ClassSummary::packageName).distinct().sorted().toList();
        int edgeCount = graph.map(g -> g.edges().size()).orElse(0);

        String context = "Project summary: " + phase4
                + "\n\nTechnology stack: " + phase2
                + "\n\nArchitecture assessment: " + phase7
                + "\n\nPackages (" + packages.size() + "): " + String.join(", ", packages.stream().limit(30).toList())
                + "\n\nClass count: " + classes.size() + ", Edge count: " + edgeCount
                + "\n\nKey classes: " + classes.stream()
                    .sorted(Comparator.comparingInt((ClassSummary c) -> c.methods().size()).reversed())
                    .limit(15)
                    .map(c -> c.qualifiedName() + " (" + c.methods().size() + " methods)")
                    .collect(Collectors.joining(", "));

        String response = queryLlm.prompt(
                """
                You are a software architect writing arc42 documentation. Generate chapters 1-5 in AsciiDoc format.
                Be specific to this project — use actual class names, packages, and technologies found in the data.
                
                Structure:
                = Chapter 1: Introduction and Goals
                == Requirements Overview (what the system does)
                == Quality Goals (top 3-5, inferred from architecture)
                
                = Chapter 2: Constraints
                == Technical Constraints (language, framework, runtime)
                
                = Chapter 3: Context and Scope
                == Business Context (what external systems it interacts with)
                == Technical Context (protocols, interfaces)
                
                = Chapter 4: Solution Strategy
                == Technology decisions (why these frameworks/libraries)
                == Architecture approach
                
                = Chapter 5: Building Block View
                == Level 1 (top-level modules/packages and their responsibilities)
                == Key components (most important classes and what they do)
                """,
                "Generate arc42 documentation for this project:\n\n" + context);

        return ToolCallResult.success(response);
    }
}
