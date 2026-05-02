CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE chunks (
  id UUID PRIMARY KEY,
  source_path TEXT NOT NULL,
  source_name TEXT NOT NULL,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  embedding vector(768) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunks_fts ON chunks USING gin (to_tsvector('english', content));

CREATE TABLE qa (
  id UUID PRIMARY KEY,
  question TEXT NOT NULL,
  answer TEXT NOT NULL,
  source_chunk_ids UUID[] NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE feedback (
  id UUID PRIMARY KEY,
  qa_id UUID NOT NULL REFERENCES qa(id) ON DELETE CASCADE,
  thumbs_up BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
