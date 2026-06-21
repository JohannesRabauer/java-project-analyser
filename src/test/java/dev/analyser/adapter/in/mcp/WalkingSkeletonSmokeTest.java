package dev.analyser.adapter.in.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.analyser.adapter.in.mcp.handler.AnalyseProjectHandler;
import dev.analyser.adapter.in.mcp.handler.GetAnalysisStatusHandler;
import dev.analyser.adapter.in.mcp.handler.GetRelatedClassesHandler;
import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import dev.analyser.application.port.out.CachePort;
import dev.analyser.domain.model.*;
import dev.analyser.domain.service.AnalysisPipelineService;
import dev.analyser.domain.service.JavaParserAstService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke test proving the walking skeleton works:
 * analyse_project → wait → get_related_classes returns graph data.
 */
class WalkingSkeletonSmokeTest {

    @TempDir
    Path projectRoot;

    private McpDispatcher dispatcher;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        createFixtureProject();

        var jobRepo = new InMemoryJobRepo();
        var cachePort = new InMemoryCachePort();
        var astService = new JavaParserAstService();

        var pipelineService = new AnalysisPipelineService(
                jobRepo,
                source -> new ProjectTree(
                        projectRoot,
                        List.of(
                                projectRoot.resolve("src/main/java/com/shop/OrderService.java"),
                                projectRoot.resolve("src/main/java/com/shop/OrderRepository.java"),
                                projectRoot.resolve("src/main/java/com/shop/BaseService.java")),
                        List.of(),
                        Optional.of(projectRoot.resolve("pom.xml")),
                        Optional.of("# Shop App")),
                cachePort,
                astService);

        dispatcher = new McpDispatcher(List.of(
                new AnalyseProjectHandler(pipelineService),
                new GetAnalysisStatusHandler(pipelineService),
                new GetRelatedClassesHandler(pipelineService)));
    }

    @Test
    void fullFlowAnalyseAndQueryRelatedClasses() throws Exception {
        // 1. Trigger analysis
        var analyseResponse = dispatch("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"analyse_project","arguments":{"projectPath":"%s"}}}"""
                .formatted(projectRoot.toString().replace("\\", "\\\\")));

        assertThat(analyseResponse.get("result").get("content").get(0).get("text").asText())
                .contains("Job ID:");

        // Extract job ID from response
        String responseText = analyseResponse.get("result").get("content").get(0).get("text").asText();
        String jobId = responseText.lines()
                .filter(l -> l.startsWith("Job ID:"))
                .map(l -> l.substring("Job ID: ".length()).trim())
                .findFirst().orElseThrow();

        // 2. Wait for pipeline to complete (runs synchronously in background thread)
        Thread.sleep(1000);

        // 3. Check status
        var statusResponse = dispatch("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_analysis_status","arguments":{"jobId":"%s"}}}"""
                .formatted(jobId));

        assertThat(statusResponse.get("result").get("content").get(0).get("text").asText())
                .contains("COMPLETED");

        // 4. Query related classes
        var relatedResponse = dispatch("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_related_classes","arguments":{"jobId":"%s","className":"com.shop.OrderService"}}}"""
                .formatted(jobId));

        String relatedText = relatedResponse.get("result").get("content").get(0).get("text").asText();
        assertThat(relatedText).contains("com.shop.OrderService");
        assertThat(relatedText).contains("BaseService");  // extends
        assertThat(relatedText).contains("OrderRepository");  // depends on
    }

    @Test
    void getRelatedClassesReturnsErrorBeforeAnalysis() throws Exception {
        var response = dispatch("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_related_classes","arguments":{"jobId":"%s","className":"Foo"}}}"""
                .formatted(UUID.randomUUID()));

        assertThat(response.get("result").get("isError").asBoolean()).isTrue();
        assertThat(response.get("result").get("content").get(0).get("text").asText())
                .contains("Analysis not completed");
    }

    private void createFixtureProject() throws IOException {
        var dir = projectRoot.resolve("src/main/java/com/shop");
        Files.createDirectories(dir);

        Files.writeString(dir.resolve("BaseService.java"), """
                package com.shop;
                public abstract class BaseService {}
                """);

        Files.writeString(dir.resolve("OrderRepository.java"), """
                package com.shop;
                public interface OrderRepository {
                    String findById(String id);
                }
                """);

        Files.writeString(dir.resolve("OrderService.java"), """
                package com.shop;
                import com.shop.OrderRepository;
                public class OrderService extends BaseService {
                    private final OrderRepository repo;
                    public OrderService(OrderRepository repo) { this.repo = repo; }
                    public String getOrder(String id) { return repo.findById(id); }
                }
                """);

        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");
    }

    private JsonNode dispatch(String json) throws Exception {
        return mapper.readTree(dispatcher.dispatch(json));
    }

    // --- Minimal test doubles ---

    private static final class InMemoryJobRepo extends AnalysisJobRepository {
        private final Map<UUID, AnalysisJob> jobs = new HashMap<>();
        private final Map<UUID, List<PhaseResult>> phases = new HashMap<>();

        InMemoryJobRepo() { super(null); }
        @Override public void create(AnalysisJob job) { jobs.put(job.id(), job); }
        @Override public Optional<AnalysisJob> findById(UUID id) { return Optional.ofNullable(jobs.get(id)); }
        @Override public void updateStatus(UUID id, AnalysisStatus status) {
            var j = jobs.get(id);
            if (j != null) jobs.put(id, new AnalysisJob(j.id(), status, j.source(), j.createdAt(), Instant.now()));
        }
        @Override public void savePhaseResult(PhaseResult r) {
            phases.computeIfAbsent(r.jobId(), k -> new ArrayList<>()).add(r);
        }
        @Override public List<PhaseResult> getPhaseResults(UUID jobId) {
            return phases.getOrDefault(jobId, List.of());
        }
    }

    private static final class InMemoryCachePort implements CachePort {
        private final Map<String, PhaseResult> store = new HashMap<>();
        @Override public void save(PhaseResult r) { store.put(r.jobId() + "-" + r.phaseId(), r); }
        @Override public Optional<PhaseResult> load(UUID jobId, int phaseId) {
            return Optional.ofNullable(store.get(jobId + "-" + phaseId));
        }
        @Override public List<Integer> listCompleted(UUID jobId) {
            return store.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(jobId.toString()))
                    .map(e -> e.getValue().phaseId()).sorted().toList();
        }
    }
}
