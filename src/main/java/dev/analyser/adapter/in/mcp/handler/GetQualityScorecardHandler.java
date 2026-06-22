package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.model.ClassSummary;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;

@ApplicationScoped
public class GetQualityScorecardHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetQualityScorecardHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override public String toolName() { return "get_quality_scorecard"; }

    @Override public String description() {
        return "Returns a multi-dimensional quality scorecard rating the project on architecture, code quality, security, test health, modernity, maintainability, and documentation. Pre-computed — instant response.";
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

        var phase6 = pipelineService.getPhaseResult(jobId, 6);
        var phase7 = pipelineService.getPhaseResult(jobId, 7);
        var phase2 = pipelineService.getPhaseResult(jobId, 2);
        var phase3 = pipelineService.getPhaseResult(jobId, 3);
        var graph = pipelineService.getClassGraph(jobId);

        int totalClasses = classes.size();
        int totalLoc = classes.stream().mapToInt(ClassSummary::lineCount).sum();

        // Architecture Clarity (from phase 7 + graph)
        int circularDeps = 0; // simplified: check bidirectional edges
        if (graph.isPresent()) {
            var g = graph.get();
            for (var edge : g.edges()) {
                if (g.edgesFrom(edge.target()).stream().anyMatch(e -> e.target().equals(edge.source())))
                    circularDeps++;
            }
        }
        int archScore = circularDeps == 0 ? 5 : circularDeps < 5 ? 4 : circularDeps < 15 ? 3 : 2;

        // Code Quality (from phase 6 warnings)
        int totalWarnings = 0, highWarnings = 0;
        try {
            if (phase6.isPresent()) {
                var root = mapper.readTree(phase6.get());
                totalWarnings = root.has("totalWarnings") ? root.get("totalWarnings").asInt() : 0;
                if (root.has("bySeverity") && root.get("bySeverity").has("HIGH"))
                    highWarnings = root.get("bySeverity").get("HIGH").asInt();
            }
        } catch (Exception ignored) {}
        double warningsPerKloc = totalLoc > 0 ? (totalWarnings * 1000.0 / totalLoc) : 0;
        int codeScore = warningsPerKloc < 2 ? 5 : warningsPerKloc < 5 ? 4 : warningsPerKloc < 15 ? 3 : warningsPerKloc < 30 ? 2 : 1;

        // Security (from phase 6 SECURITY category)
        int securityIssues = 0;
        try {
            if (phase6.isPresent()) {
                var root = mapper.readTree(phase6.get());
                if (root.has("byCategory") && root.get("byCategory").has("SECURITY"))
                    securityIssues = root.get("byCategory").get("SECURITY").asInt();
            }
        } catch (Exception ignored) {}
        int securityScore = securityIssues == 0 ? 5 : securityIssues < 3 ? 4 : securityIssues < 6 ? 3 : securityIssues < 10 ? 2 : 1;

        // Test Health
        long testClasses = classes.stream().filter(c -> c.qualifiedName().contains("Test")).count();
        long prodClasses = totalClasses - testClasses;
        int testRatio = prodClasses > 0 ? (int) (testClasses * 100 / prodClasses) : 0;
        int testScore = testRatio > 80 ? 5 : testRatio > 50 ? 4 : testRatio > 30 ? 3 : testRatio > 10 ? 2 : 1;

        // Modernity (from phase 2 tech stack)
        int modernScore = 3; // default
        try {
            if (phase2.isPresent()) {
                String tech = phase2.get();
                if (tech.contains("Quarkus") || tech.contains("Spring")) modernScore++;
                if (tech.contains("Jakarta") || tech.contains("jakarta")) modernScore++;
                modernScore = Math.min(5, modernScore);
            }
        } catch (Exception ignored) {}

        // Maintainability (complexity hotspots)
        long godClasses = classes.stream().filter(c -> c.methods().size() > 20).count();
        long complexClasses = classes.stream().filter(c -> c.lineCount() > 300).count();
        int maintScore = (godClasses + complexClasses) == 0 ? 5 :
                (godClasses + complexClasses) < 5 ? 4 :
                (godClasses + complexClasses) < 15 ? 3 :
                (godClasses + complexClasses) < 30 ? 2 : 1;

        // Documentation
        boolean hasReadme = pipelineService.getPhaseResult(jobId, 4).isPresent();
        long publicMethods = classes.stream().flatMap(c -> c.methods().stream()).filter(m -> m.isPublic()).count();
        int docScore = hasReadme ? 3 : 2; // simplified

        // Overall
        double overall = (archScore + codeScore + securityScore + testScore + modernScore + maintScore + docScore) / 7.0;

        var sb = new StringBuilder();
        sb.append(String.format("Overall Score: %.1f/5\n\n", overall));
        sb.append(formatDimension("Architecture Clarity", archScore, archJustification(archScore, circularDeps)));
        sb.append(formatDimension("Code Quality", codeScore, String.format("%d warnings (%.1f per KLOC), %d HIGH severity", totalWarnings, warningsPerKloc, highWarnings)));
        sb.append(formatDimension("Security Posture", securityScore, securityIssues + " security findings from static analysis"));
        sb.append(formatDimension("Test Health", testScore, String.format("%d test classes / %d prod classes (%d%% ratio)", testClasses, prodClasses, testRatio)));
        sb.append(formatDimension("Modernity", modernScore, "Based on detected frameworks and patterns"));
        sb.append(formatDimension("Maintainability", maintScore, String.format("%d god classes, %d complex classes (>300 LOC)", godClasses, complexClasses)));
        sb.append(formatDimension("Documentation", docScore, "README: " + (hasReadme ? "present" : "missing") + ", " + publicMethods + " public methods"));
        sb.append(String.format("\nTotal: %d classes, %d LOC", totalClasses, totalLoc));

        return ToolCallResult.success(sb.toString());
    }

    private String formatDimension(String name, int score, String justification) {
        String bar = "█".repeat(score) + "░".repeat(5 - score);
        return String.format("%s  %s  %d/5 — %s\n", bar, padRight(name, 22), score, justification);
    }

    private String padRight(String s, int n) { return String.format("%-" + n + "s", s); }

    private String archJustification(int score, int circularDeps) {
        if (score >= 4) return "Clean structure" + (circularDeps > 0 ? ", " + circularDeps + " circular deps" : "");
        return circularDeps + " circular dependencies detected — review package structure";
    }
}
