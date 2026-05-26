package dev.analyser.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AnalysisJob(
        UUID id,
        AnalysisStatus status,
        ProjectSource source,
        Instant createdAt,
        Instant updatedAt) {

    public AnalysisJob {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public static AnalysisJob create(UUID id, ProjectSource source, Instant createdAt) {
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        return new AnalysisJob(id, AnalysisStatus.PENDING, source, createdAt, createdAt);
    }

    public AnalysisJob start(Instant updatedAt) {
        ensureTransitionAllowed(AnalysisStatus.RUNNING, AnalysisStatus.PENDING, AnalysisStatus.FAILED);
        return withStatus(AnalysisStatus.RUNNING, updatedAt);
    }

    public AnalysisJob complete(Instant updatedAt) {
        ensureTransitionAllowed(AnalysisStatus.COMPLETED, AnalysisStatus.RUNNING);
        return withStatus(AnalysisStatus.COMPLETED, updatedAt);
    }

    public AnalysisJob index(Instant updatedAt) {
        ensureTransitionAllowed(AnalysisStatus.INDEXED, AnalysisStatus.COMPLETED);
        return withStatus(AnalysisStatus.INDEXED, updatedAt);
    }

    public AnalysisJob fail(Instant updatedAt) {
        ensureTransitionAllowed(AnalysisStatus.FAILED, AnalysisStatus.RUNNING);
        return withStatus(AnalysisStatus.FAILED, updatedAt);
    }

    private AnalysisJob withStatus(AnalysisStatus nextStatus, Instant nextUpdatedAt) {
        Objects.requireNonNull(nextUpdatedAt, "updatedAt must not be null");

        if (nextUpdatedAt.isBefore(updatedAt)) {
            throw new IllegalArgumentException("updatedAt must not move backwards");
        }

        return new AnalysisJob(id, nextStatus, source, createdAt, nextUpdatedAt);
    }

    private void ensureTransitionAllowed(AnalysisStatus targetStatus, AnalysisStatus... allowedCurrentStates) {
        for (var allowedCurrentState : allowedCurrentStates) {
            if (status == allowedCurrentState) {
                return;
            }
        }

        throw new IllegalStateException("Cannot transition from " + status + " to " + targetStatus);
    }
}
