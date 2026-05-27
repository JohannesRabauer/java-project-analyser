CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE rag_chunks
    ALTER COLUMN embedding TYPE VECTOR(384)
    USING REPLACE(REPLACE(embedding::text, '{', '['), '}', ']')::VECTOR(384);

CREATE INDEX IF NOT EXISTS idx_rag_chunks_job_id ON rag_chunks (job_id);
CREATE INDEX IF NOT EXISTS idx_rag_chunks_embedding_cosine ON rag_chunks USING hnsw (embedding vector_cosine_ops);
