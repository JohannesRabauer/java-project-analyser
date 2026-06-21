package dev.analyser.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import dev.analyser.application.port.out.CachePort;
import dev.analyser.application.port.out.ProjectSourcePort;
import dev.analyser.domain.model.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin orchestrator for the analysis pipeline.
 * Phase 1: AST parsing + class graph construction (JavaParser).
 * Future phases (LLM, static analysis, RAG) will be added as vertical slices.
 */
@ApplicationScoped
public class AnalysisPipelineService {

    private final AnalysisJobRepository jobRepository;
    private final ProjectSourcePort projectSourcePort;
    private final CachePort cachePort;
    private final JavaParserAstService astService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    // In-memory store of computed class graphs per job (later: persist to DB)
    private final Map<UUID, ClassGraph> graphStore = new ConcurrentHashMap<>();

    public AnalysisPipelineService(
            AnalysisJobRepository jobRepository,
            ProjectSourcePort projectSourcePort,
            CachePort cachePort,
            JavaParserAstService astService) {
        this.jobRepository = jobRepository;
        this.projectSourcePort = projectSourcePort;
        this.cachePort = cachePort;
        this.astService = astService;
    }

    public AnalysisJob startAnalysis(UUID jobId, ProjectSource source) {
        var job = AnalysisJob.create(jobId, source, Instant.now());
        jobRepository.create(job);
        jobRepository.updateStatus(jobId, AnalysisStatus.RUNNING);

        executor.submit(() -> runPipeline(job.start(Instant.now())));
        return job;
    }

    public Optional<AnalysisJob> getJob(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    public List<PhaseResult> getPhaseResults(UUID jobId) {
        return jobRepository.getPhaseResults(jobId);
    }

    public Optional<ClassGraph> getClassGraph(UUID jobId) {
        return Optional.ofNullable(graphStore.get(jobId));
    }

    void runPipeline(AnalysisJob job) {
        UUID jobId = job.id();
        try {
            var cachedPhases = loadCachedPhases(jobId);

            // Load project tree
            ProjectTree tree = projectSourcePort.loadProject(job.source());

            // Phase 1: AST parsing + graph construction
            PhaseResult phase1 = reuseOrRun(cachedPhases, jobId, 1, () -> runPhase1(jobId, tree));
            jobRepository.savePhaseResult(phase1);

            // Mark completed (future phases will be added here)
            jobRepository.updateStatus(jobId, AnalysisStatus.COMPLETED);
        } catch (Exception e) {
            jobRepository.updateStatus(jobId, AnalysisStatus.FAILED);
        }
    }

    private PhaseResult runPhase1(UUID jobId, ProjectTree tree) {
        List<ClassSummary> allClasses = new ArrayList<>();
        for (var file : tree.javaSourceFiles()) {
            allClasses.addAll(astService.parseFile(file));
        }
        for (var file : tree.testSourceFiles()) {
            allClasses.addAll(astService.parseFile(file));
        }

        ClassGraph graph = ClassGraph.buildFrom(allClasses);
        graphStore.put(jobId, graph);

        // Serialize summary for cache
        String resultJson = serializePhase1Result(graph, allClasses);
        var result = new PhaseResult(jobId, 1, PhaseStatus.COMPLETED, resultJson, Instant.now());
        cachePort.save(result);
        return result;
    }

    private String serializePhase1Result(ClassGraph graph, List<ClassSummary> classes) {
        var summary = Map.of(
                "classCount", classes.size(),
                "packageCount", classes.stream().map(ClassSummary::packageName).distinct().count(),
                "edgeCount", graph.edges().size(),
                "classes", classes.stream().map(ClassSummary::qualifiedName).toList()
        );
        try {
            return mapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }

    private Map<Integer, PhaseResult> loadCachedPhases(UUID jobId) {
        var completed = new HashSet<>(cachePort.listCompleted(jobId));
        var cached = new LinkedHashMap<Integer, PhaseResult>();
        for (int phase = 1; phase <= 8; phase++) {
            if (!completed.contains(phase)) break;
            cachePort.load(jobId, phase)
                    .filter(r -> r.status() == PhaseStatus.COMPLETED)
                    .ifPresent(r -> cached.put(r.phaseId(), r));
            if (!cached.containsKey(phase)) break;
        }
        return cached;
    }

    private PhaseResult reuseOrRun(Map<Integer, PhaseResult> cached, UUID jobId, int phaseId,
                                   java.util.function.Supplier<PhaseResult> execution) {
        var cachedResult = cached.get(phaseId);
        if (cachedResult != null) {
            // Rebuild in-memory state from cached result if needed
            return cachedResult;
        }
        return execution.get();
    }
}
