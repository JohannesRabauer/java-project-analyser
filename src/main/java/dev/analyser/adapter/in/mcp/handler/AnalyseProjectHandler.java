package dev.analyser.adapter.in.mcp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.analyser.adapter.in.mcp.McpProtocol.ToolCallResult;
import dev.analyser.adapter.in.mcp.McpToolHandler;
import dev.analyser.domain.model.LocalSource;
import dev.analyser.domain.model.ProjectSource;
import dev.analyser.domain.service.AnalysisPipelineService;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class AnalyseProjectHandler implements McpToolHandler {

    private final AnalysisPipelineService pipelineService;

    public AnalyseProjectHandler(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String toolName() { return "analyse_project"; }

    @Override
    public String description() {
        return "Triggers the analysis pipeline on a Java project. Provide projectPath (absolute filesystem path) or gitUrl. Returns the jobId for status queries. Lightweight — returns immediately, analysis runs in background.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "projectPath", Map.of("type", "string", "description", "Absolute path of the Java project on the server filesystem"),
                        "gitUrl", Map.of("type", "string", "description", "Public Git repository URL to clone and analyse")));
    }

    @Override
    public ToolCallResult handle(JsonNode args) {
        String projectPath = textOrEmpty(args, "projectPath");
        String gitUrl = textOrEmpty(args, "gitUrl");

        if (projectPath.isEmpty() && gitUrl.isEmpty()) {
            return ToolCallResult.error("Provide either 'projectPath' or 'gitUrl'");
        }

        ProjectSource source;
        String description;
        if (!projectPath.isEmpty()) {
            source = new LocalSource(Path.of(projectPath));
            description = "path: " + projectPath;
        } else {
            source = new dev.analyser.domain.model.GitSource(URI.create(gitUrl));
            description = "git: " + gitUrl;
        }

        UUID jobId = UUID.randomUUID();
        pipelineService.startAnalysis(jobId, source);

        return ToolCallResult.success(String.format(
                "Analysis started for %s\nJob ID: %s\nUse get_analysis_status to track progress.", description, jobId));
    }

    private String textOrEmpty(JsonNode args, String field) {
        if (args == null || !args.hasNonNull(field)) return "";
        return args.get(field).asText().trim();
    }
}
