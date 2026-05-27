CREATE TABLE analysis_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    project_path TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE phase_results (
    job_id UUID NOT NULL,
    phase_id INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_json TEXT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (job_id, phase_id)
);

CREATE TABLE rag_chunks (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    phase_id INT NOT NULL,
    content TEXT NOT NULL,
    -- Flyway V3 promotes this column to pgvector VECTOR(384) at runtime.
    -- jOOQ's DDLDatabase parser in this build cannot parse VECTOR types yet.
    embedding TEXT NOT NULL
);
