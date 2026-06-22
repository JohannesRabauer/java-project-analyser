package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.model.ClassSummary;
import dev.analyser.domain.model.ClassSummary.ClassKind;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class GetProjectConventionsHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;

    public GetProjectConventionsHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override public String toolName() { return "get_project_conventions"; }

    @Override public String description() {
        return "Detects and returns the coding conventions used in this project: naming patterns, package structure, DI style, test patterns. Use this before writing new code to match the project's style. Pre-computed — instant response.";
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

        var sb = new StringBuilder("Project Conventions (detected from source)\n\n");

        // Package structure
        var packages = classes.stream().map(ClassSummary::packageName).distinct().sorted().toList();
        sb.append("## Package Structure\n");
        var topLevelPkgs = packages.stream()
                .map(p -> { String[] parts = p.split("\\."); return parts.length >= 3 ? String.join(".", Arrays.copyOf(parts, 3)) : p; })
                .distinct().toList();
        boolean byLayer = packages.stream().anyMatch(p -> p.contains("adapter") || p.contains("controller") || p.contains("service") || p.contains("repository"));
        boolean byFeature = packages.stream().filter(p -> p.split("\\.").length > 3).map(p -> p.split("\\.")[3]).distinct().count() > 3;
        sb.append("- Organization: ").append(byLayer ? "By layer (adapter/service/repository)" : byFeature ? "By feature" : "Flat").append("\n");
        sb.append("- Root package: ").append(topLevelPkgs.isEmpty() ? "unknown" : topLevelPkgs.get(0)).append("\n");
        sb.append("- Total packages: ").append(packages.size()).append("\n\n");

        // Naming conventions
        sb.append("## Naming Conventions\n");
        boolean hasIPrefix = classes.stream().filter(c -> c.kind() == ClassKind.INTERFACE).anyMatch(c -> c.className().startsWith("I") && Character.isUpperCase(c.className().charAt(1)));
        boolean suffixInterfaces = classes.stream().filter(c -> c.kind() == ClassKind.INTERFACE).anyMatch(c -> c.className().endsWith("able") || c.className().endsWith("Port") || c.className().endsWith("Repository"));
        sb.append("- Interface naming: ").append(hasIPrefix ? "I-prefix (IService)" : suffixInterfaces ? "Descriptive suffix (Port, Repository, able)" : "Plain names").append("\n");

        boolean implSuffix = classes.stream().anyMatch(c -> c.className().endsWith("Impl"));
        boolean adapterSuffix = classes.stream().anyMatch(c -> c.className().endsWith("Adapter"));
        sb.append("- Implementation naming: ").append(implSuffix ? "Impl suffix" : adapterSuffix ? "Adapter/Handler suffix" : "Descriptive names").append("\n");

        // DI style
        sb.append("\n## Dependency Injection\n");
        boolean constructorInjection = classes.stream().anyMatch(c -> c.fields().stream().anyMatch(f -> f.annotations().contains("Inject")));
        boolean hasCdiAnnotations = classes.stream().anyMatch(c -> c.annotations().stream().anyMatch(a -> a.contains("ApplicationScoped") || a.contains("RequestScoped")));
        boolean hasSpringAnnotations = classes.stream().anyMatch(c -> c.annotations().stream().anyMatch(a -> a.contains("Component") || a.contains("Service") || a.contains("Repository")));
        sb.append("- Framework: ").append(hasCdiAnnotations ? "CDI (Jakarta)" : hasSpringAnnotations ? "Spring" : "Unknown").append("\n");
        sb.append("- Style: Constructor injection (").append(hasConstructorInjection(classes) ? "detected" : "not detected").append(")\n");

        // Records vs classes
        long records = classes.stream().filter(c -> c.kind() == ClassKind.RECORD).count();
        long regularClasses = classes.stream().filter(c -> c.kind() == ClassKind.CLASS).count();
        sb.append("\n## Data Modeling\n");
        sb.append("- Records: ").append(records).append(" (").append(regularClasses > 0 ? (records * 100 / (records + regularClasses)) : 0).append("% of data types)\n");
        sb.append("- Preferred DTO style: ").append(records > regularClasses / 4 ? "Java records" : "Traditional classes").append("\n");

        // Test conventions
        var testClasses = classes.stream().filter(c -> c.qualifiedName().contains("Test")).toList();
        sb.append("\n## Test Conventions\n");
        sb.append("- Test classes: ").append(testClasses.size()).append("\n");
        boolean junit5 = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.contains("org.junit.jupiter")));
        boolean testcontainers = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.contains("testcontainers")));
        sb.append("- Framework: ").append(junit5 ? "JUnit 5" : "JUnit 4 or other").append("\n");
        sb.append("- Testcontainers: ").append(testcontainers ? "Yes" : "No").append("\n");
        boolean bddStyle = testClasses.stream().anyMatch(c -> c.methods().stream().anyMatch(m -> m.name().startsWith("should")));
        boolean givenWhenThen = testClasses.stream().anyMatch(c -> c.methods().stream().anyMatch(m -> m.name().contains("When") || m.name().contains("Given")));
        sb.append("- Naming style: ").append(bddStyle ? "BDD (should...)" : givenWhenThen ? "Given/When/Then" : "Descriptive").append("\n");

        // Annotations usage
        sb.append("\n## Common Annotations\n");
        var annotationCounts = classes.stream().flatMap(c -> c.annotations().stream())
                .collect(Collectors.groupingBy(a -> a, Collectors.counting()));
        annotationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sb.append("- @").append(e.getKey()).append(" (").append(e.getValue()).append(" usages)\n"));

        return ToolCallResult.success(sb.toString());
    }

    private boolean hasConstructorInjection(List<ClassSummary> classes) {
        return classes.stream().anyMatch(c ->
                !c.fields().isEmpty() && c.methods().stream().noneMatch(m ->
                        m.annotations().stream().anyMatch(a -> a.contains("Inject") || a.contains("Autowired"))));
    }
}
