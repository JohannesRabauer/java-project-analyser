package dev.analyser.application.port.out;

import dev.analyser.domain.model.PhaseResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CachePort {

    void save(PhaseResult phaseResult);

    Optional<PhaseResult> load(UUID jobId, int phaseId);

    List<Integer> listCompleted(UUID jobId);
}
