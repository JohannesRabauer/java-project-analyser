package dev.analyser.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalysisJobTest {

    @Test
    void uc001_shouldCreateAPendingJob() {
        var createdAt = Instant.parse("2026-01-01T10:15:30Z");

        var job = AnalysisJob.create(UUID.randomUUID(), new LocalSource(Path.of("/workspace/project")), createdAt);

        assertEquals(AnalysisStatus.PENDING, job.status());
        assertEquals(createdAt, job.createdAt());
        assertEquals(createdAt, job.updatedAt());
    }

    @Test
    void uc001_shouldStartAJobFromPending() {
        var job = AnalysisJob.create(
                UUID.randomUUID(),
                new LocalSource(Path.of("/workspace/project")),
                Instant.parse("2026-01-01T10:15:30Z"));
        var startedAt = Instant.parse("2026-01-01T10:16:30Z");

        var startedJob = job.start(startedAt);

        assertEquals(AnalysisStatus.RUNNING, startedJob.status());
        assertEquals(startedAt, startedJob.updatedAt());
    }

    @Test
    void uc002_shouldAllowRestartFromFailed() {
        var createdAt = Instant.parse("2026-01-01T10:15:30Z");
        var failedAt = Instant.parse("2026-01-01T10:17:30Z");
        var restartedAt = Instant.parse("2026-01-01T10:18:30Z");

        var failedJob = AnalysisJob.create(UUID.randomUUID(), new LocalSource(Path.of("/workspace/project")), createdAt)
                .start(Instant.parse("2026-01-01T10:16:30Z"))
                .fail(failedAt);

        var restartedJob = failedJob.start(restartedAt);

        assertEquals(AnalysisStatus.RUNNING, restartedJob.status());
        assertEquals(restartedAt, restartedJob.updatedAt());
    }

    @Test
    void uc001_shouldCompleteARunningJob() {
        var completedAt = Instant.parse("2026-01-01T10:17:30Z");

        var completedJob = AnalysisJob.create(
                        UUID.randomUUID(),
                        new LocalSource(Path.of("/workspace/project")),
                        Instant.parse("2026-01-01T10:15:30Z"))
                .start(Instant.parse("2026-01-01T10:16:30Z"))
                .complete(completedAt);

        assertEquals(AnalysisStatus.COMPLETED, completedJob.status());
        assertEquals(completedAt, completedJob.updatedAt());
    }

    @Test
    void uc001_br017_shouldIndexACompletedJob() {
        var indexedAt = Instant.parse("2026-01-01T10:18:30Z");

        var indexedJob = AnalysisJob.create(
                        UUID.randomUUID(),
                        new LocalSource(Path.of("/workspace/project")),
                        Instant.parse("2026-01-01T10:15:30Z"))
                .start(Instant.parse("2026-01-01T10:16:30Z"))
                .complete(Instant.parse("2026-01-01T10:17:30Z"))
                .index(indexedAt);

        assertEquals(AnalysisStatus.INDEXED, indexedJob.status());
        assertEquals(indexedAt, indexedJob.updatedAt());
    }

    @Test
    void uc001_shouldFailARunningJob() {
        var failedAt = Instant.parse("2026-01-01T10:17:30Z");

        var failedJob = AnalysisJob.create(
                        UUID.randomUUID(),
                        new LocalSource(Path.of("/workspace/project")),
                        Instant.parse("2026-01-01T10:15:30Z"))
                .start(Instant.parse("2026-01-01T10:16:30Z"))
                .fail(failedAt);

        assertEquals(AnalysisStatus.FAILED, failedJob.status());
        assertEquals(failedAt, failedJob.updatedAt());
    }

    @Test
    void uc001_shouldRejectInvalidTransitions() {
        var pendingJob = AnalysisJob.create(
                UUID.randomUUID(),
                new LocalSource(Path.of("/workspace/project")),
                Instant.parse("2026-01-01T10:15:30Z"));

        assertThrows(IllegalStateException.class, () -> pendingJob.complete(Instant.parse("2026-01-01T10:16:30Z")));
        assertThrows(IllegalStateException.class, () -> pendingJob.index(Instant.parse("2026-01-01T10:16:30Z")));
        assertThrows(IllegalStateException.class, () -> pendingJob.fail(Instant.parse("2026-01-01T10:16:30Z")));
    }

    @Test
    void uc001_shouldRejectAnUpdatedTimestampBeforeCreation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnalysisJob(
                        UUID.randomUUID(),
                        AnalysisStatus.PENDING,
                        new LocalSource(Path.of("/workspace/project")),
                        Instant.parse("2026-01-01T10:15:30Z"),
                        Instant.parse("2026-01-01T10:14:30Z")));
    }
}
