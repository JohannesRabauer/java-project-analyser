-- Enforce referential integrity between child tables and analysis_jobs, and add the
-- missing lookup index on phase_results(job_id). Child rows are removed when their job is.

DELETE FROM phase_results
    WHERE job_id NOT IN (SELECT id FROM analysis_jobs);

DELETE FROM rag_chunks
    WHERE job_id NOT IN (SELECT id FROM analysis_jobs);

ALTER TABLE phase_results
    ADD CONSTRAINT fk_phase_results_job_id
    FOREIGN KEY (job_id) REFERENCES analysis_jobs (id) ON DELETE CASCADE;

ALTER TABLE rag_chunks
    ADD CONSTRAINT fk_rag_chunks_job_id
    FOREIGN KEY (job_id) REFERENCES analysis_jobs (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_phase_results_job_id ON phase_results (job_id);
