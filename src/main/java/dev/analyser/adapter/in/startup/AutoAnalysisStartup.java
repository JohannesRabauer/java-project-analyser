package dev.analyser.adapter.in.startup;

import dev.analyser.domain.model.GitSource;
import dev.analyser.domain.model.LocalSource;
import dev.analyser.domain.model.ProjectSource;
import dev.analyser.domain.service.AnalysisPipelineService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Auto-triggers analysis on startup when PROJECT_URL is configured.
 * This enables the "docker compose up → MCP server ready" demo flow.
 */
@ApplicationScoped
public class AutoAnalysisStartup {

    private static final Logger LOG = Logger.getLogger(AutoAnalysisStartup.class);

    private final AnalysisPipelineService pipelineService;

    @ConfigProperty(name = "analyser.project-url", defaultValue = "")
    String projectUrl;

    public AutoAnalysisStartup(AnalysisPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    void onStart(@Observes StartupEvent event) {
        if (projectUrl.isBlank()) {
            LOG.info("No PROJECT_URL configured — server ready for manual analysis via MCP tools.");
            return;
        }

        LOG.infof("PROJECT_URL configured: %s — starting auto-analysis...", projectUrl);

        ProjectSource source;
        if (projectUrl.startsWith("http://") || projectUrl.startsWith("https://") || projectUrl.startsWith("git://")) {
            source = new GitSource(URI.create(projectUrl));
        } else {
            source = new LocalSource(Path.of(projectUrl));
        }

        UUID jobId = UUID.randomUUID();
        pipelineService.startAnalysis(jobId, source);
        LOG.infof("Analysis started — Job ID: %s. Track progress at http://localhost:8080/", jobId);
    }
}
