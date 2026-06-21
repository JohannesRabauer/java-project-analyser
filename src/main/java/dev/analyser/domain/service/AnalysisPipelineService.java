package dev.analyser.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import dev.analyser.adapter.out.persistence.RagRepository;
import dev.analyser.application.port.out.CachePort;
import dev.analyser.application.port.out.EmbeddingPort;
import dev.analyser.application.port.out.LlmPort;
import dev.analyser.application.port.out.ProjectSourcePort;
import dev.analyser.domain.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@ApplicationScoped
public class AnalysisPipelineService {

    private final AnalysisJobRepository jobRepository;
    private final ProjectSourcePort projectSourcePort;
    private final CachePort cachePort;
    private final JavaParserAstService astService;
    private final LlmPort pipelineLlm;
    private final EmbeddingPort embeddingPort;
    private final RagRepository ragRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    private final Map<UUID, ClassGraph> graphStore = new ConcurrentHashMap<>();
    private final Map<UUID, List<ClassSummary>> classStore = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, String>> phaseResultStore = new ConcurrentHashMap<>();

    public AnalysisPipelineService(
            AnalysisJobRepository jobRepository,
            ProjectSourcePort projectSourcePort,
            CachePort cachePort,
            JavaParserAstService astService,
            @Named("pipelineLlm") LlmPort pipelineLlm,
            EmbeddingPort embeddingPort,
            RagRepository ragRepository) {
        this.jobRepository = jobRepository;
        this.projectSourcePort = projectSourcePort;
        this.cachePort = cachePort;
        this.astService = astService;
        this.pipelineLlm = pipelineLlm;
        this.embeddingPort = embeddingPort;
        this.ragRepository = ragRepository;
    }

    // Constructor for tests without LLM/embedding
    public AnalysisPipelineService(
            AnalysisJobRepository jobRepository,
            ProjectSourcePort projectSourcePort,
            CachePort cachePort,
            JavaParserAstService astService) {
        this(jobRepository, projectSourcePort, cachePort, astService, null, null, null);
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

    public Optional<String> getPhaseResult(UUID jobId, int phaseId) {
        var stored = phaseResultStore.getOrDefault(jobId, Map.of());
        return Optional.ofNullable(stored.get(phaseId));
    }

    public List<ClassSummary> getClasses(UUID jobId) {
        return classStore.getOrDefault(jobId, List.of());
    }

    void runPipeline(AnalysisJob job) {
        UUID jobId = job.id();
        try {
            var cached = loadCachedPhases(jobId);
            ProjectTree tree = projectSourcePort.loadProject(job.source());

            // Phase 1: AST + graph
            savePhase(jobId, reuseOrRun(cached, jobId, 1, () -> runPhase1(jobId, tree)));

            // Phase 2: Dependencies (structural)
            savePhase(jobId, reuseOrRun(cached, jobId, 2, () -> runPhase2(jobId, tree)));

            // Phase 3: Graph metrics
            savePhase(jobId, reuseOrRun(cached, jobId, 3, () -> runPhase3(jobId)));

            // Phase 4: LLM project summary
            savePhase(jobId, reuseOrRun(cached, jobId, 4, () -> runPhase4(jobId, tree)));

            // Phase 5: LLM class purpose summaries
            savePhase(jobId, reuseOrRun(cached, jobId, 5, () -> runPhase5(jobId)));

            // Phase 6: Static analysis
            savePhase(jobId, reuseOrRun(cached, jobId, 6, () -> runPhase6(jobId)));

            // Phase 7: LLM architecture assessment
            savePhase(jobId, reuseOrRun(cached, jobId, 7, () -> runPhase7(jobId)));

            jobRepository.updateStatus(jobId, AnalysisStatus.COMPLETED);

            // Phase 8: RAG embedding & indexing
            runPhase8(jobId);

            jobRepository.updateStatus(jobId, AnalysisStatus.INDEXED);
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
        classStore.put(jobId, allClasses);

        String json = toJson(Map.of(
                "classCount", allClasses.size(),
                "packageCount", allClasses.stream().map(ClassSummary::packageName).distinct().count(),
                "edgeCount", graph.edges().size(),
                "classes", allClasses.stream().map(ClassSummary::qualifiedName).toList()));
        return phaseResult(jobId, 1, json);
    }

    private PhaseResult runPhase2(UUID jobId, ProjectTree tree) {
        // Parse build descriptor for dependencies
        var deps = new ArrayList<Map<String, String>>();
        tree.buildDescriptor().ifPresent(bd -> {
            try {
                String content = Files.readString(bd);
                var matcher = java.util.regex.Pattern.compile(
                        "<dependency>\\s*<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>",
                        java.util.regex.Pattern.DOTALL).matcher(content);
                while (matcher.find()) {
                    deps.add(Map.of("groupId", matcher.group(1), "artifactId", matcher.group(2)));
                }
            } catch (Exception ignored) {}
        });

        // Detect technologies from imports
        Set<String> techs = new LinkedHashSet<>();
        var endpoints = new ArrayList<Map<String, String>>();

        for (var cls : classStore.getOrDefault(jobId, List.of())) {
            for (String imp : cls.imports()) {
                if (imp.startsWith("io.quarkus")) techs.add("Quarkus");
                else if (imp.startsWith("org.springframework")) techs.add("Spring");
                else if (imp.startsWith("jakarta.ws.rs")) techs.add("JAX-RS");
                else if (imp.startsWith("org.jooq")) techs.add("jOOQ");
                else if (imp.startsWith("com.github.javaparser")) techs.add("JavaParser");
                else if (imp.startsWith("dev.langchain4j")) techs.add("LangChain4J");
            }

            // Detect REST endpoints from class-level and method-level annotations
            boolean isRestController = cls.annotations().stream()
                    .anyMatch(a -> a.equals("Path") || a.equals("RestController") || a.equals("Controller"));
            if (isRestController) {
                for (var method : cls.methods()) {
                    for (String ann : method.annotations()) {
                        String httpMethod = switch (ann) {
                            case "GET", "GetMapping" -> "GET";
                            case "POST", "PostMapping" -> "POST";
                            case "PUT", "PutMapping" -> "PUT";
                            case "DELETE", "DeleteMapping" -> "DELETE";
                            case "PATCH", "PatchMapping" -> "PATCH";
                            default -> null;
                        };
                        if (httpMethod != null) {
                            endpoints.add(Map.of(
                                    "method", httpMethod,
                                    "handler", cls.className() + "." + method.name(),
                                    "controller", cls.qualifiedName()));
                        }
                    }
                }
            }
        }

        String json = toJson(Map.of("dependencies", deps, "technologies", techs, "endpoints", endpoints));
        return phaseResult(jobId, 2, json);
    }

    private PhaseResult runPhase3(UUID jobId) {
        var graph = graphStore.get(jobId);
        var classes = classStore.getOrDefault(jobId, List.of());
        if (graph == null) return phaseResult(jobId, 3, "{}");

        // Package metrics
        var packages = classes.stream().collect(Collectors.groupingBy(ClassSummary::packageName));
        var pkgMetrics = new ArrayList<Map<String, Object>>();
        for (var entry : packages.entrySet()) {
            int classCount = entry.getValue().size();
            long internalEdges = graph.edges().stream()
                    .filter(e -> e.source().startsWith(entry.getKey()) && e.target().startsWith(entry.getKey()))
                    .count();
            long externalEdges = graph.edges().stream()
                    .filter(e -> e.source().startsWith(entry.getKey()) && !e.target().startsWith(entry.getKey()))
                    .count();
            pkgMetrics.add(Map.of("package", entry.getKey(), "classCount", classCount,
                    "internalEdges", internalEdges, "externalEdges", externalEdges));
        }

        // Complexity hotspots
        var hotspots = classes.stream()
                .filter(c -> c.methods().size() > 10 || c.lineCount() > 200)
                .map(c -> Map.of("class", (Object)c.qualifiedName(), "methods", c.methods().size(), "loc", c.lineCount()))
                .toList();

        String json = toJson(Map.of("packageMetrics", pkgMetrics, "complexityHotspots", hotspots));
        return phaseResult(jobId, 3, json);
    }

    private PhaseResult runPhase4(UUID jobId, ProjectTree tree) {
        if (pipelineLlm == null) {
            return phaseResult(jobId, 4, toJson(Map.of("summary", "LLM not configured", "purpose", "unknown", "classification", "unknown")));
        }
        var classes = classStore.getOrDefault(jobId, List.of());
        String readme = tree.readmeContent().orElse("No README");
        String classNames = classes.stream().map(ClassSummary::qualifiedName).limit(50).collect(Collectors.joining(", "));

        String response = pipelineLlm.prompt(
                "You are a software analyst. Respond in JSON with keys: summary, purpose, classification, mainFunctionalities (array).",
                "Analyze this Java project.\nREADME:\n" + readme + "\n\nClasses: " + classNames +
                        "\n\nProvide: 1) A 1-3 sentence executive summary, 2) business purpose, 3) classification (one of: Web Application, Backend Service, Library, Batch Processor, CLI Tool), 4) main functionalities list.");

        return phaseResult(jobId, 4, response);
    }

    private PhaseResult runPhase5(UUID jobId) {
        var classes = classStore.getOrDefault(jobId, List.of());
        if (pipelineLlm == null || classes.isEmpty()) {
            return phaseResult(jobId, 5, "{}");
        }

        var summaries = new LinkedHashMap<String, String>();
        // Batch: 10 classes per LLM call
        var batch = new ArrayList<ClassSummary>();
        for (var cls : classes) {
            if (cls.lineCount() < 10) continue;
            batch.add(cls);
            if (batch.size() >= 10) {
                summarizeBatch(batch, summaries);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) summarizeBatch(batch, summaries);

        return phaseResult(jobId, 5, toJson(summaries));
    }

    private void summarizeBatch(List<ClassSummary> batch, Map<String, String> out) {
        var sb = new StringBuilder("For each class below, provide a one-sentence purpose description. Respond as JSON: {\"className\": \"purpose\", ...}\n\n");
        for (var cls : batch) {
            sb.append(cls.qualifiedName()).append(": ")
                    .append(cls.kind()).append(", methods=[")
                    .append(cls.methods().stream().map(m -> m.name()).collect(Collectors.joining(",")))
                    .append("], fields=[")
                    .append(cls.fields().stream().map(f -> f.type() + " " + f.name()).collect(Collectors.joining(",")))
                    .append("]\n");
        }
        try {
            String response = pipelineLlm.prompt("You are a code analyst. Respond only in valid JSON.", sb.toString());
            // Try to parse, if valid use it
            var map = mapper.readValue(response, Map.class);
            for (var entry : ((Map<String, Object>) map).entrySet()) {
                out.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        } catch (Exception e) {
            // Fallback: store raw response
            for (var cls : batch) {
                out.putIfAbsent(cls.qualifiedName(), "Purpose extraction failed");
            }
        }
    }

    private PhaseResult runPhase6(UUID jobId) {
        var classes = classStore.getOrDefault(jobId, List.of());
        var warnings = new ArrayList<Map<String, Object>>();

        for (var cls : classes) {
            // God class
            if (cls.methods().size() > 20) {
                warnings.add(warning(cls, "god-class", "HIGH", "Class has " + cls.methods().size() + " methods — consider splitting"));
            }
            // Large class
            if (cls.lineCount() > 500) {
                warnings.add(warning(cls, "large-class", "MEDIUM", "Class has " + cls.lineCount() + " lines"));
            }
            // Missing public methods on non-interface
            if (cls.kind() == ClassSummary.ClassKind.CLASS && cls.methods().stream().noneMatch(m -> m.isPublic())) {
                warnings.add(warning(cls, "no-public-api", "LOW", "Class has no public methods"));
            }
            // System.out usage
            if (cls.imports().stream().anyMatch(i -> i.contains("System"))) {
                // Heuristic — check field/method names isn't reliable, but annotations can hint
            }
            // Empty interface (marker interface without justification)
            if (cls.kind() == ClassSummary.ClassKind.INTERFACE && cls.methods().isEmpty()) {
                warnings.add(warning(cls, "empty-interface", "LOW", "Interface declares no methods (marker interface)"));
            }
            // High method complexity (too many parameters)
            for (var method : cls.methods()) {
                if (method.parameterTypes().size() > 5) {
                    warnings.add(warning(cls, "too-many-params", "MEDIUM",
                            "Method " + method.name() + " has " + method.parameterTypes().size() + " parameters — consider a parameter object"));
                }
            }
            // Class depends on too many others (high efferent coupling)
            if (cls.imports().size() > 20) {
                warnings.add(warning(cls, "high-coupling", "MEDIUM",
                        "Class imports " + cls.imports().size() + " types — high coupling"));
            }
        }

        return phaseResult(jobId, 6, toJson(Map.of("warnings", warnings, "totalWarnings", warnings.size())));
    }

    private PhaseResult runPhase7(UUID jobId) {
        if (pipelineLlm == null) {
            return phaseResult(jobId, 7, toJson(Map.of("style", "unknown", "assessment", "LLM not configured")));
        }
        var graph = graphStore.get(jobId);
        var classes = classStore.getOrDefault(jobId, List.of());

        var packages = classes.stream().map(ClassSummary::packageName).distinct().sorted().toList();
        boolean hasAdapters = packages.stream().anyMatch(p -> p.contains("adapter"));
        boolean hasPorts = packages.stream().anyMatch(p -> p.contains("port"));
        boolean hasController = classes.stream().anyMatch(c -> c.annotations().stream().anyMatch(a -> a.contains("Controller") || a.contains("Path")));

        String context = "Packages: " + String.join(", ", packages) +
                "\nEdge count: " + (graph != null ? graph.edges().size() : 0) +
                "\nHas adapters/ports pattern: " + (hasAdapters && hasPorts) +
                "\nHas REST controllers: " + hasController +
                "\nClass count: " + classes.size();

        String response = pipelineLlm.prompt(
                "You are a software architect. Respond in JSON with keys: architecturalStyle, strengths (array), risks (array), recommendations (array).",
                "Assess the architecture of this Java project:\n" + context);

        return phaseResult(jobId, 7, response);
    }

    private void runPhase8(UUID jobId) {
        if (embeddingPort == null || ragRepository == null) return;

        var classes = classStore.getOrDefault(jobId, List.of());
        var chunks = new ArrayList<RagChunk>();

        // Chunk each class: source summary + methods as one chunk per class
        for (var cls : classes) {
            String content = buildChunkContent(cls);
            if (content.length() < 50) continue;

            float[] embedding = embeddingPort.embed(content);
            double[] vector = toDoubleArray(embedding);
            chunks.add(new RagChunk(UUID.randomUUID(), jobId, 8, content, vector));
        }

        // Also embed all phase result summaries
        for (int phase = 1; phase <= 7; phase++) {
            var resultJson = phaseResultStore.getOrDefault(jobId, Map.of()).get(phase);
            if (resultJson != null && resultJson.length() > 50) {
                String phaseContent = "Phase " + phase + " result:\n" + resultJson;
                float[] embedding = embeddingPort.embed(phaseContent);
                chunks.add(new RagChunk(UUID.randomUUID(), jobId, 8, phaseContent, toDoubleArray(embedding)));
            }
        }

        if (!chunks.isEmpty()) {
            ragRepository.saveAll(chunks);
        }
    }

    private String buildChunkContent(ClassSummary cls) {
        var sb = new StringBuilder();
        sb.append("Class: ").append(cls.qualifiedName()).append(" (").append(cls.kind()).append(")\n");
        if (cls.superClass() != null) sb.append("Extends: ").append(cls.superClass()).append("\n");
        if (!cls.interfaces().isEmpty()) sb.append("Implements: ").append(String.join(", ", cls.interfaces())).append("\n");
        if (!cls.annotations().isEmpty()) sb.append("Annotations: ").append(String.join(", ", cls.annotations())).append("\n");
        sb.append("Methods: ");
        sb.append(cls.methods().stream()
                .map(m -> m.returnType() + " " + m.name() + "(" + String.join(", ", m.parameterTypes()) + ")")
                .collect(Collectors.joining(", ")));
        sb.append("\nFields: ");
        sb.append(cls.fields().stream().map(f -> f.type() + " " + f.name()).collect(Collectors.joining(", ")));
        return sb.toString();
    }

    private double[] toDoubleArray(float[] floats) {
        double[] doubles = new double[floats.length];
        for (int i = 0; i < floats.length; i++) doubles[i] = floats[i];
        return doubles;
    }

    private void savePhase(UUID jobId, PhaseResult result) {
        cachePort.save(result);
        jobRepository.savePhaseResult(result);
        phaseResultStore.computeIfAbsent(jobId, k -> new ConcurrentHashMap<>()).put(result.phaseId(), result.resultJson());
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
        if (cachedResult != null) return cachedResult;
        return execution.get();
    }

    private PhaseResult phaseResult(UUID jobId, int phaseId, String json) {
        return new PhaseResult(jobId, phaseId, PhaseStatus.COMPLETED, json, Instant.now());
    }

    private Map<String, Object> warning(ClassSummary cls, String rule, String severity, String message) {
        return Map.of("class", cls.qualifiedName(), "rule", rule, "severity", severity, "message", message);
    }

    private String toJson(Object obj) {
        try { return mapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}
