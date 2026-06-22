package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;

/**
 * Checks project dependencies against known vulnerability patterns.
 * Uses Phase 2 dependency data + known-vulnerable version ranges.
 */
@ApplicationScoped
public class GetDependencyVulnerabilitiesHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;
    private final ObjectMapper mapper = new ObjectMapper();

    // Known vulnerable artifacts (simplified — a real implementation would use OSV/NVD API)
    private static final Map<String, String> KNOWN_VULNERABILITIES = Map.ofEntries(
            Map.entry("org.apache.logging.log4j:log4j-core", "CVE-2021-44228 (Log4Shell) — upgrade to 2.17.1+"),
            Map.entry("org.apache.struts:struts2-core", "CVE-2017-5638 — critical RCE, upgrade immediately"),
            Map.entry("com.fasterxml.jackson.core:jackson-databind", "Multiple CVEs in versions < 2.14 — ensure latest"),
            Map.entry("org.springframework:spring-web", "CVE-2022-22965 (Spring4Shell) in 5.3.0-5.3.17 — upgrade"),
            Map.entry("commons-collections:commons-collections", "CVE-2015-7501 — deserialization RCE, use 4.x"),
            Map.entry("org.yaml:snakeyaml", "CVE-2022-1471 — RCE via deserialization, upgrade to 2.0+"),
            Map.entry("io.netty:netty-handler", "CVE-2023-44487 (HTTP/2 Rapid Reset) in < 4.1.100"),
            Map.entry("org.apache.commons:commons-text", "CVE-2022-42889 (Text4Shell) in < 1.10"),
            Map.entry("org.apache.tomcat.embed:tomcat-embed-core", "Multiple CVEs — ensure latest patch version"),
            Map.entry("com.google.protobuf:protobuf-java", "CVE-2022-3171 — DoS in < 3.21.7")
    );

    public GetDependencyVulnerabilitiesHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override public String toolName() { return "get_dependency_vulnerabilities"; }

    @Override public String description() {
        return "Checks project dependencies against known vulnerability patterns (Log4Shell, Spring4Shell, etc). Pre-computed from build descriptor — instant response.";
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

        var phase2 = pipelineService.getPhaseResult(jobId, 2);
        if (phase2.isEmpty()) return ToolCallResult.error("Analysis not completed. Run analyse_project first.");

        try {
            var root = mapper.readTree(phase2.get());
            var deps = root.get("dependencies");
            if (deps == null || !deps.isArray()) return ToolCallResult.success("No dependencies found to check.");

            var findings = new ArrayList<String>();
            for (var dep : deps) {
                String groupId = dep.has("groupId") ? dep.get("groupId").asText() : "";
                String artifactId = dep.has("artifactId") ? dep.get("artifactId").asText() : "";
                String coord = groupId + ":" + artifactId;

                var vuln = KNOWN_VULNERABILITIES.get(coord);
                if (vuln != null) {
                    findings.add("⚠️  " + coord + "\n   " + vuln);
                }
            }

            if (findings.isEmpty()) {
                return ToolCallResult.success("No known vulnerabilities found in " + deps.size() + " dependencies.\nNote: This checks against a curated list of high-profile CVEs. For comprehensive scanning, use OWASP Dependency-Check or Snyk.");
            }

            var sb = new StringBuilder("Dependency Vulnerability Report\n");
            sb.append("Checked ").append(deps.size()).append(" dependencies, found ").append(findings.size()).append(" potential issues:\n\n");
            findings.forEach(f -> sb.append(f).append("\n\n"));
            sb.append("Recommendation: Run `mvn org.owasp:dependency-check-maven:check` for a comprehensive CVE scan.");
            return ToolCallResult.success(sb.toString());
        } catch (Exception e) {
            return ToolCallResult.error("Failed to parse dependency data: " + e.getMessage());
        }
    }
}
