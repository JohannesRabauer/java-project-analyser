-- Allow varying embedding dimensions by removing the fixed VECTOR(384) constraint.
-- The dimension is now determined by the configured embedding model at runtime.
-- OpenAI text-embedding-3-small = 1536, Ollama nomic-embed-text = 384/768.

ALTER TABLE rag_chunks
    ALTER COLUMN embedding TYPE vector
    USING embedding::vector;
