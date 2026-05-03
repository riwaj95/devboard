CREATE TABLE users (
  id UUID PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('USER', 'ADMIN')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE qa ADD COLUMN user_id UUID REFERENCES users(id);
ALTER TABLE feedback ADD COLUMN user_id UUID REFERENCES users(id);

CREATE TABLE corpus_state (
  id INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  source_path TEXT,
  file_count INT NOT NULL DEFAULT 0,
  chunk_count INT NOT NULL DEFAULT 0,
  last_ingested_at TIMESTAMPTZ,
  last_ingested_by_user_id UUID REFERENCES users(id)
);
INSERT INTO corpus_state (id) VALUES (1);
