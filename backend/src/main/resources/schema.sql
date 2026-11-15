CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS users (
  id            BIGSERIAL PRIMARY KEY,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS repos (
  id          BIGSERIAL PRIMARY KEY,
  owner_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  url         TEXT NOT NULL,
  status      TEXT NOT NULL,
  message     TEXT,
  file_count  INT NOT NULL DEFAULT 0,
  chunk_count INT NOT NULL DEFAULT 0,
  created_at  TIMESTAMP NOT NULL DEFAULT now(),
  UNIQUE (owner_id, url)
);

CREATE TABLE IF NOT EXISTS chunks (
  id         BIGSERIAL PRIMARY KEY,
  repo_id    BIGINT NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
  path       TEXT NOT NULL,
  start_line INT NOT NULL,
  end_line   INT NOT NULL,
  content    TEXT NOT NULL,
  embedding  vector(768) NOT NULL
);

CREATE INDEX IF NOT EXISTS chunks_repo_idx ON chunks(repo_id);
CREATE INDEX IF NOT EXISTS chunks_embedding_idx ON chunks USING hnsw (embedding vector_cosine_ops);
