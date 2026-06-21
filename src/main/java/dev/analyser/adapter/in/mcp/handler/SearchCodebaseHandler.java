package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.adapter.out.persistence.RagRepository;
import dev.analyser.application.port.out.EmbeddingPort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class SearchCodebaseHandler implements McpToolHandler {

    private final RagRepository ragRepository;
    private final EmbeddingPort embeddingPort;

    public SearchCodebaseHandler(RagRepository ragRepository, EmbeddingPort embeddingPort) {
        this.ragRepository = ragRepository;
        this.embeddingPort = embeddingPort;
    }

    @Override
    public String toolName() { return "search_codebase"; }

    @Override
    public String description() {
        return "Semantic search over indexed code chunks. Returns top-K relevant code segments. Medium cost — embedding + vector query. May take 2-5 seconds.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job"),
                        "query", Map.of("type", "string", "description", "Natural language search query")),
                "required", List.of("jobId", "query"));
    }

    @Override
    public ToolCallResult handle(JsonNode args) {
        if (args == null || !args.has("jobId") || !args.has("query")) {
            return ToolCallResult.error("Missing required parameters: jobId, query");
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(args.get("jobId").asText());
        } catch (IllegalArgumentException e) {
            return ToolCallResult.error("Invalid jobId format");
        }

        String query = args.get("query").asText().trim();
        if (query.isEmpty()) {
            return ToolCallResult.error("Query must not be empty");
        }

        float[] embedding = embeddingPort.embed(query);
        double[] vector = toDoubleArray(embedding);

        var results = ragRepository.search(jobId, vector, 5);
        if (results.isEmpty()) {
            return ToolCallResult.success("No relevant code segments found for query: " + query);
        }

        var sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            var result = results.get(i);
            sb.append("--- Result ").append(i + 1)
              .append(" (similarity: ").append(String.format("%.3f", result.similarity())).append(") ---\n")
              .append(result.chunk().content()).append("\n\n");
        }
        return ToolCallResult.success(sb.toString().trim());
    }

    private double[] toDoubleArray(float[] floats) {
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) {
            doubles[i] = floats[i];
        }
        return doubles;
    }
}
