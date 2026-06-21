package dev.analyser.domain.service;

import dev.analyser.adapter.out.persistence.AnalysisJobRepository;
import dev.analyser.adapter.out.persistence.RagRepository;
import dev.analyser.application.port.out.CachePort;
import dev.analyser.application.port.out.EmbeddingPort;
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

/**
 * Integration test proving the full 8-phase pipeline works including RAG indexing
 * with a mock embedding port (no Docker/Ollama required).
 */
class FullPipelineIntegrationTest {

    @TempDir
    Path projectRoot;

    private InMemoryJobRepo jobRepo;
    private InMemoryRagRepo ragRepo;
    private AnalysisPipelineService service;

    @BeforeEach
    void setUp() throws IOException {
        createMultiClassProject();
        jobRepo = new InMemoryJobRepo();
        var cachePort = new InMemoryCachePort();
        ragRepo = new InMemoryRagRepo();
        var astService = new JavaParserAstService();

        service = new AnalysisPipelineService(
                jobRepo,
                source -> new ProjectTree(
                        projectRoot,
                        List.of(
                                projectRoot.resolve("src/main/java/com/shop/model/Order.java"),
                                projectRoot.resolve("src/main/java/com/shop/repo/OrderRepository.java"),
                                projectRoot.resolve("src/main/java/com/shop/service/OrderService.java"),
                                projectRoot.resolve("src/main/java/com/shop/api/OrderController.java")),
                        List.of(projectRoot.resolve("src/test/java/com/shop/service/OrderServiceTest.java")),
                        Optional.of(projectRoot.resolve("pom.xml")),
                        Optional.of("# Shopping App\nA microservice for order management.")),
                cachePort,
                astService,
                null, // no LLM in test
                new DeterministicEmbeddingPort(),
                ragRepo);
    }

    @Test
    void fullPipelineCompletesAllPhasesAndIndexesRag() {
        var jobId = UUID.randomUUID();
        var job = AnalysisJob.create(jobId, new LocalSource(projectRoot), Instant.now());
        jobRepo.create(job);

        service.runPipeline(job.start(Instant.now()));

        assertThat(jobRepo.lastStatus(jobId)).isEqualTo(AnalysisStatus.INDEXED);
        assertThat(jobRepo.getPhaseResults(jobId)).hasSize(7);
        assertThat(ragRepo.savedChunks).isNotEmpty();
    }

    @Test
    void phase1BuildsCorrectClassGraph() {
        var jobId = UUID.randomUUID();
        var job = AnalysisJob.create(jobId, new LocalSource(projectRoot), Instant.now());
        jobRepo.create(job);

        service.runPipeline(job.start(Instant.now()));

        var graph = service.getClassGraph(jobId);
        assertThat(graph).isPresent();
        assertThat(graph.get().classNames()).contains(
                "com.shop.model.Order",
                "com.shop.repo.OrderRepository",
                "com.shop.service.OrderService",
                "com.shop.api.OrderController");

        // OrderService depends on OrderRepository
        var related = graph.get().relatedTo("com.shop.service.OrderService");
        assertThat(related.dependsOn()).contains("com.shop.repo.OrderRepository");

        // OrderController depends on OrderService
        var controllerRelated = graph.get().relatedTo("com.shop.api.OrderController");
        assertThat(controllerRelated.dependsOn()).contains("com.shop.service.OrderService");
    }

    @Test
    void phase2DetectsRestEndpoints() {
        var jobId = UUID.randomUUID();
        var job = AnalysisJob.create(jobId, new LocalSource(projectRoot), Instant.now());
        jobRepo.create(job);

        service.runPipeline(job.start(Instant.now()));

        var phase2 = service.getPhaseResult(jobId, 2);
        assertThat(phase2).isPresent();
        assertThat(phase2.get()).contains("GET");
        assertThat(phase2.get()).contains("OrderController");
    }

    @Test
    void phase6DetectsStaticAnalysisWarnings() {
        var jobId = UUID.randomUUID();
        var job = AnalysisJob.create(jobId, new LocalSource(projectRoot), Instant.now());
        jobRepo.create(job);

        service.runPipeline(job.start(Instant.now()));

        var phase6 = service.getPhaseResult(jobId, 6);
        assertThat(phase6).isPresent();
        assertThat(phase6.get()).contains("warnings");
    }

    @Test
    void ragSearchReturnsRelevantChunks() {
        var jobId = UUID.randomUUID();
        var job = AnalysisJob.create(jobId, new LocalSource(projectRoot), Instant.now());
        jobRepo.create(job);

        service.runPipeline(job.start(Instant.now()));

        // Search with same embedding as "OrderService" class chunk
        assertThat(ragRepo.savedChunks).isNotEmpty();
        assertThat(ragRepo.savedChunks.stream()
                .anyMatch(c -> c.content().contains("OrderService"))).isTrue();
    }

    private void createMultiClassProject() throws IOException {
        var model = projectRoot.resolve("src/main/java/com/shop/model");
        var repo = projectRoot.resolve("src/main/java/com/shop/repo");
        var svc = projectRoot.resolve("src/main/java/com/shop/service");
        var api = projectRoot.resolve("src/main/java/com/shop/api");
        var test = projectRoot.resolve("src/test/java/com/shop/service");
        Files.createDirectories(model);
        Files.createDirectories(repo);
        Files.createDirectories(svc);
        Files.createDirectories(api);
        Files.createDirectories(test);

        Files.writeString(model.resolve("Order.java"), """
                package com.shop.model;
                public record Order(String id, String customerId, double total) {}
                """);

        Files.writeString(repo.resolve("OrderRepository.java"), """
                package com.shop.repo;
                import com.shop.model.Order;
                public interface OrderRepository {
                    Order findById(String id);
                    void save(Order order);
                }
                """);

        Files.writeString(svc.resolve("OrderService.java"), """
                package com.shop.service;
                import com.shop.model.Order;
                import com.shop.repo.OrderRepository;
                public class OrderService {
                    private final OrderRepository repository;
                    public OrderService(OrderRepository repository) { this.repository = repository; }
                    public Order getOrder(String id) { return repository.findById(id); }
                    public void placeOrder(Order order) { repository.save(order); }
                }
                """);

        Files.writeString(api.resolve("OrderController.java"), """
                package com.shop.api;
                import com.shop.model.Order;
                import com.shop.service.OrderService;
                import jakarta.ws.rs.GET;
                import jakarta.ws.rs.POST;
                import jakarta.ws.rs.Path;
                @Path("/orders")
                public class OrderController {
                    private final OrderService orderService;
                    public OrderController(OrderService orderService) { this.orderService = orderService; }
                    @GET
                    public Order get(String id) { return orderService.getOrder(id); }
                    @POST
                    public void create(Order order) { orderService.placeOrder(order); }
                }
                """);

        Files.writeString(test.resolve("OrderServiceTest.java"), """
                package com.shop.service;
                import org.junit.jupiter.api.Test;
                class OrderServiceTest {
                    @Test void testGetOrder() {}
                }
                """);

        Files.writeString(projectRoot.resolve("pom.xml"), """
                <project>
                  <groupId>com.shop</groupId>
                  <artifactId>shop-service</artifactId>
                  <dependencies>
                    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-rest</artifactId></dependency>
                    <dependency><groupId>org.jooq</groupId><artifactId>jooq</artifactId></dependency>
                  </dependencies>
                </project>
                """);
    }

    /** Deterministic embedding: hash-based, matches by content similarity for same text */
    private static class DeterministicEmbeddingPort implements EmbeddingPort {
        @Override
        public float[] embed(String text) {
            float[] embedding = new float[384];
            var rand = new Random(text.hashCode());
            double sum = 0;
            for (int i = 0; i < 384; i++) {
                embedding[i] = (float) rand.nextGaussian();
                sum += embedding[i] * embedding[i];
            }
            float norm = (float) Math.sqrt(sum);
            for (int i = 0; i < 384; i++) embedding[i] /= norm;
            return embedding;
        }
    }

    // --- Test doubles ---
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
        AnalysisStatus lastStatus(UUID jobId) { return jobs.get(jobId).status(); }
    }

    private static final class InMemoryRagRepo extends RagRepository {
        final List<RagChunk> savedChunks = new ArrayList<>();
        InMemoryRagRepo() { super(null); }
        @Override public void saveAll(List<RagChunk> chunks) { savedChunks.addAll(chunks); }
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
