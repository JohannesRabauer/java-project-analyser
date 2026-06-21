package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.adapter.out.persistence.RagRepository;
import dev.analyser.application.port.out.EmbeddingPort;
import dev.analyser.application.port.out.LlmPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class GetPossibleBugsHandler implements McpToolHandler {

    private final RagRepository ragRepository;
    private final EmbeddingPort embeddingPort;
    private final LlmPort queryLlm;

    public GetPossibleBugsHandler(RagRepository ragRepository, EmbeddingPort embeddingPort,
                                  @Named("queryLlm") LlmPort queryLlm) {
        this.ragRepository = ragRepository;
        this.embeddingPort = embeddingPort;
        this.queryLlm = queryLlm;
    }

    @Override
    public String toolName() { return "get_possible_bugs"; }

    @Override
    public String description() {
        return "LLM analyzes relevant code for potential bugs, null safety issues, race conditions, resource leaks. Heavy — 10-30 seconds.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job"),
                        "scope", Map.of("type", "string", "description", "Optional class name or package name to scope the analysis")),
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

        String scope = args.has("scope") ? args.get("scope").asText().trim() : "entire project";

        float[] embedding = embeddingPort.embed(scope);
        double[] vector = toDoubleArray(embedding);

        var results = ragRepository.search(jobId, vector, 10);
        if (results.isEmpty()) {
            return ToolCallResult.error("No indexed code found for job " + jobId + ". Run analyse_project first.");
        }

        String codeContext = results.stream()
                .map(r -> r.chunk().content())
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = "You are a senior Java code reviewer specializing in bug detection. "
                + "Analyze the provided code for potential bugs, null safety issues, race conditions, "
                + "resource leaks, and other defects. Be specific about location and severity.";

        String userPrompt = "Analyze the following code from scope '" + scope + "' for potential bugs:\n\n" + codeContext;

        String response = queryLlm.prompt(systemPrompt, userPrompt);
        return ToolCallResult.success(response);
    }

    private double[] toDoubleArray(float[] floats) {
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = floats[i];
        }
        return doubles;
    }
}
