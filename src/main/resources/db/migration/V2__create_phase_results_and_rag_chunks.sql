CREATE TABLE IF NOT EXISTS phase_results (
    job_id UUID NOT NULL,
    phase_id INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_json TEXT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (job_id, phase_id)
);

CREATE TABLE IF NOT EXISTS rag_chunks (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    phase_id INT NOT NULL,
    content TEXT NOT NULL,
    embedding DOUBLE PRECISION ARRAY NOT NULL
);
