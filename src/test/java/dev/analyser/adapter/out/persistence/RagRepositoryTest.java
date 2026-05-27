package dev.analyser.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.analyser.domain.model.RagChunk;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(PostgreSqlTestResource.class)
class RagRepositoryTest {

    @Inject
    RagRepository repository;

    @Inject
    DataSource dataSource;

    @Test
    void uc006_shouldStoreEmbeddingsUsingPgvector() throws SQLException {
        var jobId = UUID.randomUUID();

        repository.saveAll(List.of(new RagChunk(
                UUID.randomUUID(),
                jobId,
                10,
                "public class Example {}",
                embedding(1.0, 0.0))));

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                     "SELECT pg_typeof(embedding)::text FROM rag_chunks WHERE job_id = '" + jobId + "'")) {
            resultSet.next();
            assertEquals("vector", resultSet.getString(1));
        }
    }

    @Test
    void uc006_shouldApplySimilarityThresholdBeforeLimitingResults() {
        var jobId = UUID.randomUUID();

        repository.saveAll(List.of(
                new RagChunk(UUID.randomUUID(), jobId, 10, "exact match", embedding(1.0, 0.0)),
                new RagChunk(UUID.randomUUID(), jobId, 10, "orthogonal", embedding(0.0, 1.0)),
                new RagChunk(UUID.randomUUID(), jobId, 10, "below threshold", embedding(0.6, 0.8))));

        var results = repository.search(jobId, embedding(1.0, 0.0), 5);

        assertEquals(1, results.size());
        assertIterableEquals(List.of("exact match"), results.stream().map(result -> result.chunk().content()).toList());
    }

    @Test
    void uc006_shouldApplyTheV3MigrationIdempotently() throws SQLException, IOException {
        var migrationSql = loadMigrationSql("db/migration/V3__migrate_rag_embeddings_to_pgvector.sql");

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            assertTrue(statement.execute(migrationSql) || statement.getUpdateCount() >= 0);
            assertTrue(statement.execute(migrationSql) || statement.getUpdateCount() >= 0);
        }
    }

    private double[] embedding(double first, double second) {
        double[] embedding = new double[384];
        embedding[0] = first;
        embedding[1] = second;
        return embedding;
    }

    private String loadMigrationSql(String path) throws IOException {
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            Objects.requireNonNull(stream, "migration resource must exist");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
