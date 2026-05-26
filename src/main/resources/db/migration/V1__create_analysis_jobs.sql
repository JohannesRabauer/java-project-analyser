CREATE TABLE IF NOT EXISTS analysis_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    project_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
