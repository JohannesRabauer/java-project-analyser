package dev.analyser.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.analyser.domain.model.AnalysisJob;
import dev.analyser.domain.model.AnalysisStatus;
import dev.analyser.domain.model.LocalSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(PostgreSqlTestResource.class)
class AnalysisJobRepositoryTest {

    @Inject
    AnalysisJobRepository repository;

    @Inject
    DataSource dataSource;

    @Test
    void uc001_shouldRoundTripCreateFindAndUpdateStatus() {
        var jobId = UUID.randomUUID();
        var createdAt = Instant.now().minusSeconds(5);
        var job = AnalysisJob.create(jobId, new LocalSource(Path.of("/workspace/project")), createdAt);

        repository.create(job);

        var storedJob = repository.findById(jobId).orElseThrow();

        assertEquals(job.id(), storedJob.id());
        assertEquals(job.status(), storedJob.status());
        assertEquals(job.source(), storedJob.source());
        assertWithinOneMicrosecond(job.createdAt(), storedJob.createdAt());
        assertWithinOneMicrosecond(job.updatedAt(), storedJob.updatedAt());

        repository.updateStatus(jobId, AnalysisStatus.RUNNING);

        var updatedJob = repository.findById(jobId).orElseThrow();

        assertEquals(AnalysisStatus.RUNNING, updatedJob.status());
        assertEquals(job.source(), updatedJob.source());
        assertWithinOneMicrosecond(job.createdAt(), updatedJob.createdAt());
        assertFalse(updatedJob.updatedAt().isBefore(storedJob.updatedAt()));
    }

    @Test
    void uc001_shouldApplyTheV1MigrationIdempotently() throws SQLException, IOException {
        var migrationSql = loadMigrationSql();

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            assertTrue(statement.execute(migrationSql) || statement.getUpdateCount() >= 0);
            assertTrue(statement.execute(migrationSql) || statement.getUpdateCount() >= 0);
        }
    }

    private String loadMigrationSql() throws IOException {
        try (var stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("db/migration/V1__create_analysis_jobs.sql")) {
            Objects.requireNonNull(stream, "migration resource must exist");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertWithinOneMicrosecond(Instant expected, Instant actual) {
        assertTrue(Math.abs(Duration.between(expected, actual).toNanos()) <= 1_000);
    }
}
