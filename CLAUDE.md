# DevBoard — local-only weekend MVP

## What this is
A single-user chatbot that answers questions about a folder of markdown files on disk, with citations. I am the only user. I run it on my laptop.

## Hard scope — DO NOT exceed
1. I point the app at a local folder (e.g. `/Users/me/devboard-corpus`)
2. I click "Ingest" — the app reads every `.md` file in that folder, splits them into chunks, embeds each chunk with Ollama, stores them in Postgres
3. I type a question into a textbox and click "Ask"
4. The app retrieves the top 6 chunks (hybrid: pgvector cosine + Postgres full-text, merged with reciprocal rank fusion), sends them to Ollama with a strict system prompt, returns the answer with citations like `[1]`, `[2]`
5. Below each answer, a list of `[1] filename.md`, `[2] otherfile.md` showing which file each citation came from, with the file path
6. Thumbs up / thumbs down buttons that write to a `feedback` table — no UI to view feedback, I'll query the DB myself
7. Past Q&As are listed below the input, newest first

## Explicitly NOT in this MVP
- Any authentication (no login, no users table)
- Any deployment, CI/CD, or Docker for the app itself (only Postgres runs in Docker)
- GitHub or Confluence ingestion — local markdown files only
- Streaming — synchronous request/response is fine
- Admin dashboard
- Multi-tenancy
- Background jobs, schedulers, async — everything synchronous
- Tests beyond one smoke test
- Any feature I haven't explicitly listed above

## Tech stack — exact versions
- Java 21
- Spring Boot 3.3.5 (web, data-jpa, validation, thymeleaf — NO security, NO actuator)
- Maven
- PostgreSQL 16 with pgvector — image `pgvector/pgvector:pg16`, started via a one-file `docker-compose.yml`
- Liquibase for migrations (NOT Flyway). YAML master changelog, raw SQL files for changesets via `sqlFile`.
- Ollama on host: `llama3.2:3b` for chat, `nomic-embed-text` for embeddings (768 dims)
- Spring's built-in `RestClient` for talking to Ollama. Nothing else.
- Thymeleaf for HTML. NO HTMX, NO Tailwind, NO JS framework. Plain HTML form, plain CSS in a `<style>` tag.

## Maven dependencies (do not add others)
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-validation`
- `spring-boot-starter-thymeleaf`
- `org.postgresql:postgresql` (runtime)
- `org.liquibase:liquibase-core`
- (No flyway. No security. No actuator. No Lombok.)

## Project structure
```
devboard/
├── pom.xml
├── docker-compose.yml
├── .env.example
├── README.md
├── CLAUDE.md
└── src/main/
    ├── java/de/devboard/
    │   ├── DevBoardApplication.java
    │   ├── LlmClient.java
    │   ├── OllamaLlmClient.java
    │   ├── IngestionService.java
    │   ├── RetrievalService.java
    │   ├── ChatService.java
    │   ├── ChatController.java
    │   └── domain/
    └── resources/
        ├── application.yml
        ├── db/changelog/
        │   ├── db.changelog-master.yaml
        │   └── changes/
        │       └── 001-init.sql
        └── templates/index.html
```

## Liquibase configuration

### application.yml (key sections)
```yaml
spring:
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
    enabled: true
  datasource:
    url: jdbc:postgresql://localhost:5432/devboard
    username: devboard
    password: devboard
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
```

### db.changelog-master.yaml
```yaml
databaseChangeLog:
  - changeSet:
      id: 001-init
      author: devboard
      changes:
        - sqlFile:
            path: changes/001-init.sql
            relativeToChangelogFile: true
            splitStatements: true
            stripComments: true
```

### 001-init.sql
```sql
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
```

## Liquibase gotchas
- Keep changesets as raw SQL via `sqlFile`, not declarative XML/YAML — pgvector's custom type confuses declarative generators.
- Liquibase auto-creates `databasechangelog` and `databasechangeloglock` on first run. Do not include these in your migration.
- `splitStatements: true` is required so `CREATE EXTENSION` executes before the `CREATE TABLE` that uses `vector(768)`.
- `ddl-auto: validate` will refuse startup if JPA entities mismatch the schema. Map the `embedding` column with `@Column(columnDefinition = "vector(768)")` and use **native queries** for vector ops — do not try to express vector similarity in JPQL.

## Ollama API contracts
- Embeddings: `POST http://localhost:11434/api/embed` body `{"model":"nomic-embed-text","input":["text1","text2"]}` → `{"embeddings":[[768 floats], ...]}`. Batch up to 32 chunks per call.
- Chat: `POST http://localhost:11434/api/chat` body `{"model":"llama3.2:3b","messages":[...],"stream":false,"options":{"temperature":0.2}}` → `{"message":{"content":"..."}}`.

## Retrieval algorithm
1. Embed the question with Ollama → query vector
2. Vector search (native query): `SELECT id FROM chunks ORDER BY embedding <=> CAST(:qvec AS vector) LIMIT 20`
3. Full-text search (native query): `SELECT id FROM chunks WHERE to_tsvector('english', content) @@ plainto_tsquery('english', :q) LIMIT 20`
4. Reciprocal Rank Fusion: for each chunk in either list, score = sum(1 / (60 + rank_in_list)). Top 6 by score.
5. Build context as:
```
[1] from foo.md:
<chunk content>

[2] from bar.md:
<chunk content>
```
6. System prompt:
```
You answer the user's question using ONLY the context below.
Cite sources with [1], [2], etc., matching the bracketed numbers in the context.
If the context doesn't contain the answer, say "I don't see that in the docs."
Do not make up information.
```

## Chunking rules
- Split each markdown file by paragraph (double newline)
- Group paragraphs greedily up to ~600 words per chunk
- One sentence overlap between chunks
- Skip files larger than 200KB
- Skip empty chunks

## What "done" looks like
1. `docker compose up -d` starts Postgres
2. `mvn spring-boot:run` starts the app, Liquibase runs the migration on boot
3. Open `http://localhost:8080` — page shows ingest textbox and (locked) ask textbox
4. Paste `/Users/me/devboard-corpus`, click Ingest, see "Ingested N chunks from M files"
5. Type a question, get an answer with `[1] [2]` citations and clickable source list
6. Click thumbs up — page reloads, QA appears in history
7. `SELECT count(*) FROM databasechangelog;` returns 1

## Conventions
- Constructor injection only
- All entities use `UUID` primary keys
- No `@Async`, no reactive — everything synchronous
- Wrap external calls (Ollama) in try/catch, log error, show "something went wrong" on the page

## Pre-flight check
Before any code, run these and show the responses:
```
curl http://localhost:11434/api/tags
curl http://localhost:11434/api/embed -d '{"model":"nomic-embed-text","input":["hello"]}'
curl http://localhost:11434/api/chat -d '{"model":"llama3.2:3b","messages":[{"role":"user","content":"say hi"}],"stream":false}'
```
