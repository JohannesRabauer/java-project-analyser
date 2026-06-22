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
public class GetModernizationAssessmentHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;

    public GetModernizationAssessmentHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override public String toolName() { return "get_modernization_assessment"; }

    @Override public String description() {
        return "Assesses the project's modernity: Java features usage, framework versions, deprecated patterns, and upgrade opportunities. Pre-computed — instant response.";
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

        var sb = new StringBuilder("Modernization Assessment\n\n");

        // Java language features
        long records = classes.stream().filter(c -> c.kind() == ClassKind.RECORD).count();
        long enums = classes.stream().filter(c -> c.kind() == ClassKind.ENUM).count();
        boolean usesVar = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.contains("var")));
        boolean usesOptional = classes.stream().anyMatch(c -> c.methods().stream().anyMatch(m -> m.returnType().contains("Optional")));
        boolean usesStreams = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.contains("java.util.stream")));

        sb.append("## Java Language Features\n");
        sb.append("- Records: ").append(records > 0 ? "✅ " + records + " records (Java 16+)" : "❌ No records — consider for DTOs/value objects").append("\n");
        sb.append("- Sealed classes: ").append(classes.stream().anyMatch(c -> c.className().contains("sealed")) ? "✅ Used" : "⚪ Not detected").append("\n");
        sb.append("- Optional: ").append(usesOptional ? "✅ Used in return types" : "⚠️ Not detected — consider for nullable returns").append("\n");
        sb.append("- Streams: ").append(usesStreams ? "✅ Used" : "⚠️ Not detected").append("\n");
        sb.append("- Enums: ").append(enums).append(" enum types\n\n");

        // Framework modernity
        sb.append("## Framework Assessment\n");
        boolean jakartaEe = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.startsWith("jakarta.")));
        boolean javaxEe = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.startsWith("javax.") && !i.startsWith("javax.sql")));
        sb.append("- Namespace: ").append(jakartaEe ? "✅ Jakarta EE (modern)" : javaxEe ? "⚠️ javax.* (legacy — migrate to Jakarta)" : "N/A").append("\n");

        boolean springBoot3 = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.contains("org.springframework.boot"))) && jakartaEe;
        boolean quarkus = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.startsWith("io.quarkus")));
        sb.append("- Framework: ").append(quarkus ? "✅ Quarkus (modern)" : springBoot3 ? "✅ Spring Boot 3+ (Jakarta)" : "⚠️ Older framework generation").append("\n");

        boolean junit5 = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.contains("org.junit.jupiter")));
        boolean junit4 = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.contains("org.junit.Test") || i.equals("org.junit.Assert")));
        sb.append("- Test framework: ").append(junit5 ? "✅ JUnit 5" : junit4 ? "⚠️ JUnit 4 — migrate to JUnit 5" : "Unknown").append("\n\n");

        // Legacy patterns detected
        sb.append("## Legacy Patterns Detected\n");
        var legacyPatterns = new ArrayList<String>();

        long nullReturns = classes.stream().flatMap(c -> c.methods().stream())
                .filter(m -> m.returnType().equals("Object") || m.returnType().equals("String"))
                .count(); // proxy for null-returning methods

        if (javaxEe && !jakartaEe) legacyPatterns.add("javax.* imports — migrate to jakarta.* (Jakarta EE 9+)");
        if (junit4 && !junit5) legacyPatterns.add("JUnit 4 — migrate to JUnit 5 with @ExtendWith");
        if (records == 0 && classes.size() > 20) legacyPatterns.add("No records used — consider records for immutable data carriers");
        if (!usesOptional) legacyPatterns.add("No Optional usage — consider for nullable return values");

        boolean hasServiceLocator = classes.stream().anyMatch(c -> c.methods().stream().anyMatch(m -> m.name().contains("getInstance") || m.name().contains("getBean")));
        if (hasServiceLocator) legacyPatterns.add("Service locator pattern detected — prefer constructor injection");

        boolean hasCheckedExceptions = classes.stream().anyMatch(c -> c.methods().stream().anyMatch(m -> m.returnType().contains("throws")));
        boolean rawTypes = classes.stream().anyMatch(c -> c.fields().stream().anyMatch(f -> f.type().equals("List") || f.type().equals("Map") || f.type().equals("Set")));
        if (rawTypes) legacyPatterns.add("Raw generic types (List, Map without type params) — add generics");

        if (legacyPatterns.isEmpty()) {
            sb.append("✅ No significant legacy patterns detected\n");
        } else {
            legacyPatterns.forEach(p -> sb.append("- ⚠️ ").append(p).append("\n"));
        }

        // Modern patterns detected
        sb.append("\n## Modern Patterns Detected\n");
        var modernPatterns = new ArrayList<String>();
        if (records > 0) modernPatterns.add("Java records for immutable data (" + records + " records)");
        if (jakartaEe) modernPatterns.add("Jakarta EE namespace");
        if (usesOptional) modernPatterns.add("Optional for nullable returns");
        if (usesStreams) modernPatterns.add("Stream API usage");
        if (junit5) modernPatterns.add("JUnit 5 with modern extensions");
        boolean hasTestcontainers = classes.stream().anyMatch(c -> c.imports().stream().anyMatch(i -> i.contains("testcontainers")));
        if (hasTestcontainers) modernPatterns.add("Testcontainers for integration tests");
        boolean hasDI = classes.stream().anyMatch(c -> c.annotations().stream().anyMatch(a -> a.contains("ApplicationScoped") || a.contains("Inject")));
        if (hasDI) modernPatterns.add("CDI/DI for dependency management");

        if (modernPatterns.isEmpty()) {
            sb.append("⚠️ Few modern patterns detected — significant modernization opportunity\n");
        } else {
            modernPatterns.forEach(p -> sb.append("- ✅ ").append(p).append("\n"));
        }

        // Recommendations
        sb.append("\n## Upgrade Recommendations\n");
        int priority = 1;
        if (javaxEe && !jakartaEe) sb.append(priority++).append(". Migrate javax.* → jakarta.* (required for Spring Boot 3 / Quarkus 3)\n");
        if (junit4) sb.append(priority++).append(". Migrate JUnit 4 → JUnit 5 (better extension model, parameterized tests)\n");
        if (records == 0) sb.append(priority++).append(". Introduce records for DTOs and value objects (reduces boilerplate)\n");
        if (!usesOptional) sb.append(priority++).append(". Adopt Optional for nullable returns (prevents NPEs)\n");
        if (hasServiceLocator) sb.append(priority++).append(". Replace service locators with constructor injection\n");
        if (priority == 1) sb.append("✅ Project is well-modernized — no critical upgrades needed\n");

        return ToolCallResult.success(sb.toString());
    }
}
