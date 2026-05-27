package dev.analyser.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import dev.analyser.adapter.out.persistence.RagRepository;
import dev.analyser.application.port.out.CachePort;
import dev.analyser.domain.model.AnalysisJob;
import dev.analyser.domain.model.AnalysisStatus;
import dev.analyser.domain.model.LocalSource;
import dev.analyser.domain.model.PhaseResult;
import dev.analyser.domain.model.PhaseStatus;
import dev.analyser.domain.model.ProjectTree;
import dev.analyser.domain.model.RagChunk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AnalysisPipelineServiceTest {

    @Test
    void uc002_br002_shouldReuseCachedCompletedPhasesAndPersistOnlyRemainingPhases() throws IOException {
        var projectRoot = createProjectFixture("reuse-cache");
        var tree = projectTree(projectRoot);
        var jobId = UUID.randomUUID();
        var repository = new InMemoryAnalysisJobRepository();
        var ragRepository = new InMemoryRagRepository();
        var cachePort = new RecordingCachePort();
        var source = new LocalSource(projectRoot);
        var createdAt = Instant.parse("2026-01-01T10:15:30Z");
        var createdJob = AnalysisJob.create(jobId, source, createdAt);
        var startedJob = createdJob.start(createdAt.plusSeconds(1));

        repository.create(createdJob);
        cachePort.put(new PhaseResult(jobId, 1, PhaseStatus.COMPLETED, "{\"cachedPhase\":1}", createdAt.plusSeconds(2)));
        cachePort.put(new PhaseResult(jobId, 2, PhaseStatus.COMPLETED, "{\"cachedPhase\":2}", createdAt.plusSeconds(3)));
        cachePort.put(new PhaseResult(jobId, 3, PhaseStatus.COMPLETED, "{\"cachedPhase\":3}", createdAt.plusSeconds(4)));

        var service = new AnalysisPipelineService(repository, ragRepository, ignored -> tree, cachePort);

        service.runPipeline(startedJob);

        var phaseResults = repository.getPhaseResults(jobId);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), phaseResults.stream().map(PhaseResult::phaseId).toList());
        assertEquals("{\"cachedPhase\":1}", phaseResults.get(0).resultJson());
        assertEquals("{\"cachedPhase\":2}", phaseResults.get(1).resultJson());
        assertEquals("{\"cachedPhase\":3}", phaseResults.get(2).resultJson());
        assertEquals(List.of(4, 5, 6, 7, 8, 9), cachePort.savedPhaseIds());
        assertEquals(AnalysisStatus.INDEXED, repository.lastStatus(jobId));
        assertNotEquals(List.of(), ragRepository.savedChunks);
    }

    @Test
    void uc002_br001_shouldOnlyReuseTheContiguousCompletedPrefixFromCache() throws IOException {
        var projectRoot = createProjectFixture("contiguous-prefix");
        var tree = projectTree(projectRoot);
        var jobId = UUID.randomUUID();
        var repository = new InMemoryAnalysisJobRepository();
        var ragRepository = new InMemoryRagRepository();
        var cachePort = new RecordingCachePort();
        var source = new LocalSource(projectRoot);
        var createdAt = Instant.parse("2026-01-01T10:20:30Z");
        var createdJob = AnalysisJob.create(jobId, source, createdAt);
        var startedJob = createdJob.start(createdAt.plusSeconds(1));

        repository.create(createdJob);
        cachePort.put(new PhaseResult(jobId, 1, PhaseStatus.COMPLETED, "{\"cachedPhase\":1}", createdAt.plusSeconds(2)));
        cachePort.put(new PhaseResult(jobId, 3, PhaseStatus.COMPLETED, "{\"cachedPhase\":3}", createdAt.plusSeconds(4)));

        var service = new AnalysisPipelineService(repository, ragRepository, ignored -> tree, cachePort);

        service.runPipeline(startedJob);

        Map<Integer, PhaseResult> phaseResultsById = repository.getPhaseResults(jobId).stream()
                .collect(LinkedHashMap::new, (map, result) -> map.put(result.phaseId(), result), Map::putAll);
        assertEquals("{\"cachedPhase\":1}", phaseResultsById.get(1).resultJson());
        assertNotEquals("{\"cachedPhase\":3}", phaseResultsById.get(3).resultJson());
        assertEquals(List.of(2, 3, 4, 5, 6, 7, 8, 9), cachePort.savedPhaseIds());
    }

    private ProjectTree projectTree(Path projectRoot) {
        return new ProjectTree(
                projectRoot,
                List.of(projectRoot.resolve("src/main/java/dev/analyser/SampleResource.java")),
                List.of(projectRoot.resolve("src/test/java/dev/analyser/SampleResourceTest.java")),
                Optional.of(projectRoot.resolve("pom.xml")),
                Optional.of("# Sample Project\nThis project proves resume behaviour."));
    }

    private Path createProjectFixture(String name) throws IOException {
        var projectRoot = resetDirectory(Path.of("target/test-data/analysis-pipeline-service").resolve(name));
        var mainJava = projectRoot.resolve("src/main/java/dev/analyser/SampleResource.java");
        var testJava = projectRoot.resolve("src/test/java/dev/analyser/SampleResourceTest.java");
        Files.createDirectories(mainJava.getParent());
        Files.createDirectories(testJava.getParent());
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");
        Files.writeString(
                mainJava,
                """
                package dev.analyser;

                import jakarta.ws.rs.GET;
                import jakarta.ws.rs.Path;

                @Path("/sample")
                public class SampleResource {

                    @GET
                    @Path("/ping")
                    public String ping() {
                        return "pong";
                    }
                }
                """);
        Files.writeString(
                testJava,
                """
                package dev.analyser;

                class SampleResourceTest {
                }
                """);
        return projectRoot;
    }

    private Path resetDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to delete " + path, exception);
                    }
                });
            }
        }

        Files.createDirectories(directory);
        return directory;
    }

    private static final class InMemoryAnalysisJobRepository extends AnalysisJobRepository {

        private final Map<UUID, AnalysisJob> jobs = new HashMap<>();
        private final Map<UUID, Map<Integer, PhaseResult>> phaseResults = new HashMap<>();

        private InMemoryAnalysisJobRepository() {
            super(null);
        }

        @Override
        public void create(AnalysisJob analysisJob) {
            jobs.put(analysisJob.id(), analysisJob);
        }

        @Override
        public Optional<AnalysisJob> findById(UUID id) {
            return Optional.ofNullable(jobs.get(id));
        }

        @Override
        public void updateStatus(UUID id, AnalysisStatus status) {
            var existingJob = jobs.get(id);
            if (existingJob == null) {
                return;
            }

            jobs.put(id, new AnalysisJob(
                    existingJob.id(),
                    status,
                    existingJob.source(),
                    existingJob.createdAt(),
                    existingJob.updatedAt().plusSeconds(1)));
        }

        @Override
        public void savePhaseResult(PhaseResult phaseResult) {
            phaseResults.computeIfAbsent(phaseResult.jobId(), ignored -> new HashMap<>())
                    .put(phaseResult.phaseId(), phaseResult);
        }

        @Override
        public List<PhaseResult> getPhaseResults(UUID jobId) {
            return phaseResults.getOrDefault(jobId, Map.of()).values().stream()
                    .sorted(Comparator.comparingInt(PhaseResult::phaseId))
                    .toList();
        }

        private AnalysisStatus lastStatus(UUID jobId) {
            return jobs.get(jobId).status();
        }
    }

    private static final class InMemoryRagRepository extends RagRepository {

        private final List<RagChunk> savedChunks = new ArrayList<>();

        private InMemoryRagRepository() {
            super(null);
        }

        @Override
        public void saveAll(List<RagChunk> chunks) {
            savedChunks.addAll(chunks);
        }
    }

    private static final class RecordingCachePort implements CachePort {

        private final Map<Integer, PhaseResult> cachedResults = new HashMap<>();
        private final List<Integer> savedPhaseIds = new ArrayList<>();

        @Override
        public void save(PhaseResult phaseResult) {
            savedPhaseIds.add(phaseResult.phaseId());
            cachedResults.put(phaseResult.phaseId(), phaseResult);
        }

        @Override
        public Optional<PhaseResult> load(UUID jobId, int phaseId) {
            return Optional.ofNullable(cachedResults.get(phaseId))
                    .filter(result -> result.jobId().equals(jobId));
        }

        @Override
        public List<Integer> listCompleted(UUID jobId) {
            return IntStream.rangeClosed(1, 9)
                    .filter(phaseId -> load(jobId, phaseId)
                            .filter(result -> result.status() == PhaseStatus.COMPLETED)
                            .isPresent())
                    .boxed()
                    .toList();
        }

        private void put(PhaseResult phaseResult) {
            cachedResults.put(phaseResult.phaseId(), phaseResult);
        }

        private List<Integer> savedPhaseIds() {
            return List.copyOf(savedPhaseIds);
        }
    }
}
