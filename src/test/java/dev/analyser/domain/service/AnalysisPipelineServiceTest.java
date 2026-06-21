package dev.analyser.domain.service;

import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import dev.analyser.application.port.out.CachePort;
import dev.analyser.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisPipelineServiceTest {

    @TempDir
    Path projectRoot;

    private InMemoryJobRepository jobRepo;
    private RecordingCachePort cachePort;
    private AnalysisPipelineService service;

    @BeforeEach
    void setUp() throws IOException {
        createFixtureProject();
        jobRepo = new InMemoryJobRepository();
        cachePort = new RecordingCachePort();
        var astService = new JavaParserAstService();

        service = new AnalysisPipelineService(
                jobRepo,
                source -> new ProjectTree(
                        projectRoot,
                        List.of(projectRoot.resolve("src/main/java/com/example/OrderService.java")),
                        List.of(),
                        Optional.of(projectRoot.resolve("pom.xml")),
                        Optional.of("# Test Project")),
                cachePort,
                astService);
    }

    @Test
    void runPipelineCompletesAllPhasesWithAstParsing() {
        var jobId = UUID.randomUUID();
        var job = AnalysisJob.create(jobId, new LocalSource(projectRoot), Instant.now());
        jobRepo.create(job);

        service.runPipeline(job.start(Instant.now()));

        // Job should be INDEXED
        assertThat(jobRepo.lastStatus(jobId)).isEqualTo(AnalysisStatus.INDEXED);

        // All 7 phases should be saved
        var phases = jobRepo.getPhaseResults(jobId);
        assertThat(phases).hasSize(7);
        assertThat(phases.get(0).phaseId()).isEqualTo(1);
        assertThat(phases.get(0).resultJson()).contains("OrderService");

        // Cache should have all 7 phases
        assertThat(cachePort.savedPhaseIds()).containsExactly(1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void runPipelineBuildsClassGraph() {
        var jobId = UUID.randomUUID();
        var job = AnalysisJob.create(jobId, new LocalSource(projectRoot), Instant.now());
        jobRepo.create(job);

        service.runPipeline(job.start(Instant.now()));

        var graph = service.getClassGraph(jobId);
        assertThat(graph).isPresent();
        assertThat(graph.get().classNames()).contains("com.example.OrderService");
    }

    @Test
    void runPipelineReusesCachedPhase1() {
        var jobId = UUID.randomUUID();
        var job = AnalysisJob.create(jobId, new LocalSource(projectRoot), Instant.now());
        jobRepo.create(job);

        // Pre-populate cache for phase 1
        cachePort.save(new PhaseResult(jobId, 1, PhaseStatus.COMPLETED, "{\"cached\":true}", Instant.now()));

        service.runPipeline(job.start(Instant.now()));

        // Phase 1 should reuse cached result
        assertThat(jobRepo.getPhaseResults(jobId).get(0).resultJson()).isEqualTo("{\"cached\":true}");
        // Phases 2-7 should be newly computed and saved
        assertThat(cachePort.savedPhaseIds()).contains(2, 3, 4, 5, 6, 7);
    }

    private void createFixtureProject() throws IOException {
        var mainJava = projectRoot.resolve("src/main/java/com/example/OrderService.java");
        Files.createDirectories(mainJava.getParent());
        Files.writeString(mainJava, """
                package com.example;

                import jakarta.enterprise.context.ApplicationScoped;

                @ApplicationScoped
                public class OrderService {
                    public String process(String orderId) {
                        return "processed-" + orderId;
                    }
                }
                """);
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");
    }

    // --- Test doubles ---

    private static final class InMemoryJobRepository extends AnalysisJobRepository {
        private final Map<UUID, AnalysisJob> jobs = new HashMap<>();
        private final Map<UUID, List<PhaseResult>> phases = new HashMap<>();

        InMemoryJobRepository() { super(null); }

        @Override public void create(AnalysisJob job) { jobs.put(job.id(), job); }

        @Override public Optional<AnalysisJob> findById(UUID id) { return Optional.ofNullable(jobs.get(id)); }

        @Override public void updateStatus(UUID id, AnalysisStatus status) {
            var job = jobs.get(id);
            if (job != null) {
                jobs.put(id, new AnalysisJob(job.id(), status, job.source(), job.createdAt(), Instant.now()));
            }
        }

        @Override public void savePhaseResult(PhaseResult r) {
            phases.computeIfAbsent(r.jobId(), k -> new ArrayList<>()).add(r);
        }

        @Override public List<PhaseResult> getPhaseResults(UUID jobId) {
            return phases.getOrDefault(jobId, List.of());
        }

        AnalysisStatus lastStatus(UUID jobId) { return jobs.get(jobId).status(); }
    }

    private static final class RecordingCachePort implements CachePort {
        private final Map<String, PhaseResult> store = new HashMap<>();
        private final List<Integer> savedPhaseIds = new ArrayList<>();

        @Override public void save(PhaseResult r) {
            savedPhaseIds.add(r.phaseId());
            store.put(r.jobId() + "-" + r.phaseId(), r);
        }

        @Override public Optional<PhaseResult> load(UUID jobId, int phaseId) {
            return Optional.ofNullable(store.get(jobId + "-" + phaseId));
        }

        @Override public List<Integer> listCompleted(UUID jobId) {
            return store.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(jobId.toString()))
                    .map(e -> e.getValue().phaseId())
                    .sorted()
                    .toList();
        }

        List<Integer> savedPhaseIds() { return List.copyOf(savedPhaseIds); }
    }
}
