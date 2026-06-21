package dev.analyser.adapter.in.ui;

import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/")
public class LandingPageResource {

    private final Template landing;
    private final AnalysisJobRepository jobRepository;

    public LandingPageResource(Template landing, AnalysisJobRepository jobRepository) {
        this.landing = landing;
        this.jobRepository = jobRepository;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        var jobs = jobRepository.findAll();
        return landing.data("jobs", jobs);
    }
}
