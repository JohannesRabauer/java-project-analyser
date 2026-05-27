package dev.analyser.adapter.out.persistence;

import static generated.jooq.tables.AnalysisJobs.ANALYSIS_JOBS;
import static generated.jooq.tables.PhaseResults.PHASE_RESULTS;

import dev.analyser.domain.model.AnalysisJob;
import dev.analyser.domain.model.PhaseResult;
import dev.analyser.domain.model.PhaseStatus;
import dev.analyser.domain.model.AnalysisStatus;
import dev.analyser.domain.model.LocalSource;
import dev.analyser.domain.model.ProjectSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

@ApplicationScoped
public class AnalysisJobRepository {

    private final DataSource dataSource;

    public AnalysisJobRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void create(AnalysisJob analysisJob) {
        dsl().insertInto(ANALYSIS_JOBS)
                .set(ANALYSIS_JOBS.ID, analysisJob.id())
                .set(ANALYSIS_JOBS.STATUS, analysisJob.status().name())
                .set(ANALYSIS_JOBS.PROJECT_PATH, toProjectPath(analysisJob.source()))
                .set(ANALYSIS_JOBS.CREATED_AT, toOffsetDateTime(analysisJob.createdAt()))
                .set(ANALYSIS_JOBS.UPDATED_AT, toOffsetDateTime(analysisJob.updatedAt()))
                .execute();
    }

    public Optional<AnalysisJob> findById(UUID id) {
        return dsl().selectFrom(ANALYSIS_JOBS)
                .where(ANALYSIS_JOBS.ID.eq(id))
                .fetchOptional(this::toDomain);
    }

    public void updateStatus(UUID id, AnalysisStatus status) {
        dsl().update(ANALYSIS_JOBS)
                .set(ANALYSIS_JOBS.STATUS, status.name())
                .set(ANALYSIS_JOBS.UPDATED_AT, DSL.greatest(DSL.currentOffsetDateTime(), ANALYSIS_JOBS.UPDATED_AT))
                .where(ANALYSIS_JOBS.ID.eq(id))
                .execute();
    }

    private AnalysisJob toDomain(generated.jooq.tables.records.AnalysisJobsRecord record) {
        return new AnalysisJob(
                record.getId(),
                AnalysisStatus.valueOf(record.getStatus()),
                new LocalSource(Path.of(record.getProjectPath())),
                toInstant(record.getCreatedAt()),
                toInstant(record.getUpdatedAt()));
    }

    private String toProjectPath(ProjectSource source) {
        if (source instanceof LocalSource localSource) {
            return localSource.rootPath().toString();
        }

        throw new IllegalArgumentException("analysis_jobs.project_path only supports LocalSource");
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime.toInstant();
    }

    public void savePhaseResult(PhaseResult phaseResult) {
        dsl().insertInto(PHASE_RESULTS)
                .set(PHASE_RESULTS.JOB_ID, phaseResult.jobId())
                .set(PHASE_RESULTS.PHASE_ID, phaseResult.phaseId())
                .set(PHASE_RESULTS.STATUS, phaseResult.status().name())
                .set(PHASE_RESULTS.RESULT_JSON, phaseResult.resultJson())
                .set(PHASE_RESULTS.COMPLETED_AT, toOffsetDateTime(phaseResult.completedAt()))
                .onDuplicateKeyUpdate()
                .set(PHASE_RESULTS.STATUS, phaseResult.status().name())
                .set(PHASE_RESULTS.RESULT_JSON, phaseResult.resultJson())
                .set(PHASE_RESULTS.COMPLETED_AT, toOffsetDateTime(phaseResult.completedAt()))
                .execute();
    }

    public List<PhaseResult> getPhaseResults(UUID jobId) {
        return dsl().selectFrom(PHASE_RESULTS)
                .where(PHASE_RESULTS.JOB_ID.eq(jobId))
                .orderBy(PHASE_RESULTS.PHASE_ID.asc())
                .fetch(rec -> new PhaseResult(
                        rec.getJobId(),
                        rec.getPhaseId(),
                        PhaseStatus.valueOf(rec.getStatus()),
                        rec.getResultJson(),
                        toInstant(rec.getCompletedAt())
                ));
    }

    private DSLContext dsl() {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }
}
