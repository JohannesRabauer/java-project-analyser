package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.adapter.out.persistence.RagRepository;
import dev.analyser.application.port.out.EmbeddingPort;
import dev.analyser.application.port.out.LlmPort;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class SuggestImprovementHandler implements McpToolHandler {

    private final RagRepository ragRepository;
    private final EmbeddingPort embeddingPort;
    private final LlmPort queryLlm;
    private final AnalysisPipelineService pipelineService;

    public SuggestImprovementHandler(RagRepository ragRepository, EmbeddingPort embeddingPort,
                                     @Named("queryLlm") LlmPort queryLlm,
                                     AnalysisPipelineService pipelineService) {
        this.ragRepository = ragRepository;
        this.embeddingPort = embeddingPort;
        this.queryLlm = queryLlm;
        this.pipelineService = pipelineService;
    }

    @Override
    public String toolName() { return "suggest_improvement"; }

    @Override
    public String description() {
        return "LLM suggests refactoring opportunities and design improvements for a class. Heavy — 10-30 seconds.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job"),
                        "className", Map.of("type", "string", "description", "Fully qualified class name to analyze")),
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

        // Get class relationships from the graph
        var graphOpt = pipelineService.getClassGraph(jobId);
        String relationships = "";
        if (graphOpt.isPresent()) {
            var related = graphOpt.get().relatedTo(className);
            relationships = formatRelationships(related);
        }

        // Get RAG chunks relevant to this class
        float[] embedding = embeddingPort.embed(className);
        double[] vector = toDoubleArray(embedding);
        var results = ragRepository.search(jobId, vector, 10);

        if (results.isEmpty() && relationships.isEmpty()) {
            return ToolCallResult.error("No data found for class " + className + " in job " + jobId);
        }

        String codeContext = results.stream()
                .map(r -> r.chunk().content())
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = "You are a senior Java architect specializing in clean code and design patterns. "
                + "Suggest refactoring opportunities and design improvements. Consider SOLID principles, "
                + "DRY, coupling, cohesion, and testability. Be specific and actionable.";

        String userPrompt = "Suggest improvements for class '" + className + "'.\n\n"
                + "Relationships:\n" + relationships + "\n\n"
                + "Code context:\n" + codeContext;

        String response = queryLlm.prompt(systemPrompt, userPrompt);
        return ToolCallResult.success(response);
    }

    private String formatRelationships(dev.analyser.domain.model.ClassGraph.RelatedClasses r) {
        var sb = new StringBuilder();
        if (r.superClass() != null) sb.append("Extends: ").append(r.superClass()).append("\n");
        if (!r.implementsInterfaces().isEmpty()) sb.append("Implements: ").append(String.join(", ", r.implementsInterfaces())).append("\n");
        if (!r.dependsOn().isEmpty()) sb.append("Depends on: ").append(String.join(", ", r.dependsOn())).append("\n");
        if (!r.dependedOnBy().isEmpty()) sb.append("Depended on by: ").append(String.join(", ", r.dependedOnBy())).append("\n");
        return sb.toString();
    }

    private double[] toDoubleArray(float[] floats) {
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = floats[i];
        }
        return doubles;
    }
}
