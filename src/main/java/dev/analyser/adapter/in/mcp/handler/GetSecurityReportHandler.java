package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;

/**
 * Returns OWASP Top 10 security findings from the static analysis phase.
 * Filters the Phase 6 results to show only SECURITY category warnings.
 */
@ApplicationScoped
public class GetSecurityReportHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetSecurityReportHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override public String toolName() { return "get_security_report"; }

    @Override public String description() {
        return "Returns OWASP Top 10 security findings: SQL injection, hardcoded secrets, insecure deserialization, weak crypto, SSRF. Pre-computed from AST — instant response.";
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

        var phase6 = pipelineService.getPhaseResult(jobId, 6);
        if (phase6.isEmpty()) return ToolCallResult.error("Analysis not completed. Run analyse_project first.");

        try {
            var root = mapper.readTree(phase6.get());
            var warnings = root.get("warnings");
            if (warnings == null || !warnings.isArray()) return ToolCallResult.success("No security findings.");

            var securityFindings = new ArrayList<JsonNode>();
            for (var w : warnings) {
                if ("SECURITY".equals(w.has("category") ? w.get("category").asText() : ""))
                    securityFindings.add(w);
            }

            if (securityFindings.isEmpty()) {
                return ToolCallResult.success("No security vulnerabilities detected in source code.\nNote: This covers OWASP A02 (Crypto), A03 (Injection), A07 (Misconfig), A08 (Deserialization), A10 (SSRF). For runtime security, use OWASP ZAP.");
            }

            var sb = new StringBuilder("OWASP Security Report\n");
            sb.append("Found ").append(securityFindings.size()).append(" security issues:\n\n");

            // Group by severity
            var critical = securityFindings.stream().filter(w -> "CRITICAL".equals(w.get("severity").asText())).toList();
            var high = securityFindings.stream().filter(w -> "HIGH".equals(w.get("severity").asText())).toList();
            var medium = securityFindings.stream().filter(w -> "MEDIUM".equals(w.get("severity").asText())).toList();

            if (!critical.isEmpty()) {
                sb.append("🔴 CRITICAL (").append(critical.size()).append("):\n");
                critical.forEach(w -> sb.append("  • ").append(w.get("file").asText())
                        .append(":").append(w.get("line").asInt())
                        .append(" — ").append(w.get("message").asText()).append("\n"));
                sb.append("\n");
            }
            if (!high.isEmpty()) {
                sb.append("🟠 HIGH (").append(high.size()).append("):\n");
                high.forEach(w -> sb.append("  • ").append(w.get("file").asText())
                        .append(":").append(w.get("line").asInt())
                        .append(" — ").append(w.get("message").asText()).append("\n"));
                sb.append("\n");
            }
            if (!medium.isEmpty()) {
                sb.append("🟡 MEDIUM (").append(medium.size()).append("):\n");
                medium.forEach(w -> sb.append("  • ").append(w.get("file").asText())
                        .append(":").append(w.get("line").asInt())
                        .append(" — ").append(w.get("message").asText()).append("\n"));
            }

            return ToolCallResult.success(sb.toString());
        } catch (Exception e) {
            return ToolCallResult.error("Failed to parse analysis data: " + e.getMessage());
        }
    }
}
