package dev.analyser.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PhaseResult(
        UUID jobId,
        int phaseId,
        PhaseStatus status,
        String resultJson,
        Instant completedAt) {

    public PhaseResult {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(resultJson, "resultJson must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");

        if (phaseId < 1 || phaseId > 9) {
            throw new IllegalArgumentException("phaseId must be between 1 and 9");
        }

        if (resultJson.isBlank()) {
            throw new IllegalArgumentException("resultJson must not be blank");
        }
    }
}
