package dev.analyser.adapter.in.mcp;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.out.persistence.RagRepository;
import dev.analyser.domain.model.AnalysisJob;
import dev.analyser.domain.model.GitSource;
import dev.analyser.domain.model.LocalSource;
import dev.analyser.domain.model.ProjectSource;
import dev.analyser.domain.service.AnalysisPipelineService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpDispatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void uc001_shouldPreferGitSourceWhenGitUrlIsProvided() throws Exception {
        var pipelineService = new CapturingPipelineService();
        var dispatcher = new McpDispatcher(pipelineService, null, new RagRepository(null));

        var response = dispatcher.dispatch("""
                {
                  "jsonrpc": "2.0",
                  "id": 11,
                  "method": "tools/call",
                  "params": {
                    "name": "analyse_project",
                    "arguments": {
                      "projectPath": "/workspace/project",
                      "gitUrl": "https://github.com/example/project.git"
                    }
                  }
                }
                """);

        assertNotNull(mapper.readTree(response).get("result"));
        var gitSource = assertInstanceOf(GitSource.class, pipelineService.capturedSource);
        assertNotNull(pipelineService.capturedJobId);
        assertNotNull(gitSource.repositoryUrl());
    }

    @Test
    void uc001_shouldUseLocalSourceWhenGitUrlIsMissing() {
        var pipelineService = new CapturingPipelineService();
        var dispatcher = new McpDispatcher(pipelineService, null, new RagRepository(null));

        dispatcher.dispatch("""
                {
                  "jsonrpc": "2.0",
                  "id": 12,
                  "method": "tools/call",
                  "params": {
                    "name": "analyse_project",
                    "arguments": {
                      "projectPath": "/workspace/project"
                    }
                  }
                }
                """);

        assertInstanceOf(LocalSource.class, pipelineService.capturedSource);
    }

    private static final class CapturingPipelineService extends AnalysisPipelineService {

        private UUID capturedJobId;
        private ProjectSource capturedSource;

        private CapturingPipelineService() {
            super(null, null, null, null);
        }

        @Override
        public AnalysisJob startAnalysis(UUID jobId, ProjectSource source) {
            capturedJobId = jobId;
            capturedSource = source;
            return AnalysisJob.create(jobId, source, Instant.now());
        }
    }
}
