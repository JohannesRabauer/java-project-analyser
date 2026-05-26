package dev.analyser.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhaseResultTest {

    @Test
    void uc001_shouldCreateACompletedPhaseResult() {
        var completedAt = Instant.parse("2026-01-01T10:17:30Z");

        var result = new PhaseResult(
                UUID.randomUUID(),
                1,
                PhaseStatus.COMPLETED,
                "{\"summary\":\"ok\"}",
                completedAt);

        assertEquals(PhaseStatus.COMPLETED, result.status());
        assertEquals(completedAt, result.completedAt());
    }

    @Test
    void uc001_shouldAllowDegradedAndFailedStatuses() {
        var completedAt = Instant.parse("2026-01-01T10:17:30Z");

        var degraded = new PhaseResult(
                UUID.randomUUID(),
                2,
                PhaseStatus.DEGRADED,
                "{\"rawResponse\":\"schema mismatch\"}",
                completedAt);
        var failed = new PhaseResult(
                UUID.randomUUID(),
                3,
                PhaseStatus.FAILED,
                "{\"error\":\"timeout\"}",
                completedAt);

        assertEquals(PhaseStatus.DEGRADED, degraded.status());
        assertEquals(PhaseStatus.FAILED, failed.status());
    }

    @Test
    void uc001_br001_shouldRejectPhaseIdsOutsideTheSupportedRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhaseResult(
                        UUID.randomUUID(),
                        0,
                        PhaseStatus.COMPLETED,
                        "{\"summary\":\"ok\"}",
                        Instant.parse("2026-01-01T10:17:30Z")));

        assertThrows(
                IllegalArgumentException.class,
                () -> new PhaseResult(
                        UUID.randomUUID(),
                        10,
                        PhaseStatus.COMPLETED,
                        "{\"summary\":\"ok\"}",
                        Instant.parse("2026-01-01T10:17:30Z")));
    }

    @Test
    void uc001_shouldRejectBlankJsonContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PhaseResult(
                        UUID.randomUUID(),
                        1,
                        PhaseStatus.COMPLETED,
                        "   ",
                        Instant.parse("2026-01-01T10:17:30Z")));
    }
}
