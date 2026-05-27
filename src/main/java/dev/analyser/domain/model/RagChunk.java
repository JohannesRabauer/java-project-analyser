package dev.analyser.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RagChunk(
        UUID id,
        UUID jobId,
        int phaseId,
        String content,
        double[] embedding) {

    public RagChunk {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(embedding, "embedding must not be null");

        if (phaseId < 1 || phaseId > 10) {
            throw new IllegalArgumentException("phaseId must be between 1 and 10");
        }

        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
