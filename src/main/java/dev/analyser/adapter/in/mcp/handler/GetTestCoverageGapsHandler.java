package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.model.ClassSummary;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Identifies classes and public methods that likely lack test coverage
 * by cross-referencing production classes with test classes.
 */
@ApplicationScoped
public class GetTestCoverageGapsHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;

    public GetTestCoverageGapsHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override public String toolName() { return "get_test_coverage_gaps"; }

    @Override public String description() {
        return "Identifies production classes and methods without corresponding test classes. Pre-computed from AST — instant response. Helps prioritize test writing.";
    }

    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "jobId", Map.of("type", "string", "description", "The UUID of the analysis job"),
                        "packageFilter", Map.of("type", "string", "description", "Optional: only check classes in this package")),
                "required", List.of("jobId"));
    }

    @Override
    public ToolCallResult handle(JsonNode args) {
        if (args == null || !args.has("jobId")) return ToolCallResult.error("Missing required parameter: jobId");

        UUID jobId;
        try { jobId = UUID.fromString(args.get("jobId").asText()); }
        catch (IllegalArgumentException e) { return ToolCallResult.error("Invalid jobId format"); }

        var allClasses = pipelineService.getClasses(jobId);
        if (allClasses.isEmpty()) return ToolCallResult.error("Analysis not completed. Run analyse_project first.");

        String pkgFilter = args.has("packageFilter") ? args.get("packageFilter").asText() : "";

        // Separate production vs test classes
        var testClassNames = allClasses.stream()
                .filter(c -> c.qualifiedName().contains("Test") || c.qualifiedName().contains("test"))
                .map(ClassSummary::className)
                .collect(Collectors.toSet());

        var productionClasses = allClasses.stream()
                .filter(c -> !c.qualifiedName().contains("Test") && !c.qualifiedName().contains("test"))
                .filter(c -> c.kind() == ClassSummary.ClassKind.CLASS || c.kind() == ClassSummary.ClassKind.RECORD)
                .filter(c -> pkgFilter.isEmpty() || c.packageName().startsWith(pkgFilter))
                .toList();

        // Find classes without a matching test
        var untestedClasses = new ArrayList<ClassSummary>();
        for (var cls : productionClasses) {
            String expectedTest = cls.className() + "Test";
            String expectedTest2 = cls.className() + "Tests";
            if (!testClassNames.contains(expectedTest) && !testClassNames.contains(expectedTest2)) {
                untestedClasses.add(cls);
            }
        }

        // Prioritize by complexity (more methods = higher priority)
        untestedClasses.sort(Comparator.comparingInt((ClassSummary c) -> c.methods().size()).reversed());

        var sb = new StringBuilder();
        sb.append("Test Coverage Gap Analysis\n");
        sb.append("Production classes: ").append(productionClasses.size()).append("\n");
        sb.append("Test classes found: ").append(testClassNames.size()).append("\n");
        sb.append("Classes without tests: ").append(untestedClasses.size()).append("\n");
        int coverage = productionClasses.isEmpty() ? 100 :
                (int) ((productionClasses.size() - untestedClasses.size()) * 100.0 / productionClasses.size());
        sb.append("Estimated class-level coverage: ").append(coverage).append("%\n\n");

        if (untestedClasses.isEmpty()) {
            sb.append("All production classes have corresponding test classes.");
        } else {
            sb.append("Priority untested classes (by complexity):\n\n");
            int shown = 0;
            for (var cls : untestedClasses) {
                if (shown >= 30) {
                    sb.append("... and ").append(untestedClasses.size() - 30).append(" more\n");
                    break;
                }
                sb.append("• ").append(cls.qualifiedName())
                        .append(" (").append(cls.methods().size()).append(" methods, ")
                        .append(cls.lineCount()).append(" LOC)\n");
                // List public methods that need testing
                var publicMethods = cls.methods().stream().filter(m -> m.isPublic()).toList();
                if (!publicMethods.isEmpty() && publicMethods.size() <= 8) {
                    for (var m : publicMethods) {
                        sb.append("  - ").append(m.returnType()).append(" ").append(m.name()).append("()\n");
                    }
                }
                sb.append("\n");
                shown++;
            }
        }

        return ToolCallResult.success(sb.toString());
    }
}
