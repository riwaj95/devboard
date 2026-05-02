# Feature 001 — Login + per-user history

## Goal
Add email/password authentication and scope all questions/feedback to the logged-in user. The corpus stays shared (one tenant). Each user sees only their own QA history.

## Scope — what this feature adds
1. A `users` table with id, email (unique), password_hash, role (USER or ADMIN), created_at
2. A signup page at GET /signup with a form (email + password). POST /signup creates the user, hashes the password with BCrypt, logs them in, redirects to /
3. A login page at GET /login. POST /login authenticates and starts a session, redirects to /
4. POST /logout ends the session, redirects to /login
5. `qa` and `feedback` tables get a `user_id UUID NOT NULL` column referencing users(id)
6. The chat page at GET / requires authentication — anonymous users redirect to /login
7. The history list on / shows ONLY the current user's questions, newest first
8. The /ingest endpoint requires the user to have role = ADMIN. Regular users get 403 if they try.
9. The first user who signs up automatically gets role = ADMIN. Subsequent signups are USER.

## Scope — what this feature does NOT add
- No password reset, no email verification, no "remember me", no SSO, no OAuth
- No admin user management UI (admins are made automatically as described above; no promote/demote)
- No multi-tenancy (still one shared corpus)
- No role-based UI hiding (a USER seeing the Ingest button and getting 403 is fine; we'll polish that in the UI session)
- No password complexity rules beyond min 8 characters
- No CSRF tokens beyond Spring Security's defaults
- No rate limiting on login attempts

## Stack additions
- Add ONE dependency: `spring-boot-starter-security`
- No other dependencies. No JWT library. Use Spring Security's built-in form login + session cookie.

## Schema changes (new Liquibase changeset 002-add-users.sql)
```sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('USER', 'ADMIN')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE qa ADD COLUMN user_id UUID REFERENCES users(id);
ALTER TABLE feedback ADD COLUMN user_id UUID REFERENCES users(id);

-- Existing rows (from MVP testing) get a placeholder so the NOT NULL works.
-- For now they remain nullable — see "Migration concession" below.
```

## Migration concession
Existing `qa` and `feedback` rows from your MVP testing have no user. Two options:
- **Easy path: Wipe them.** Run `docker compose down -v && docker compose up -d postgres` before applying the new migration. You lose your test history but everything is clean. **Recommended.**
- **Preserve them:** Keep `user_id` nullable and treat NULL rows as "system" — they show up to admins only. More code, more edge cases. Skip this unless you actually care about your test history.

The brief should pick **wipe them** and document this in the changeset.

## Required Spring Security setup
- A `SecurityConfig` class as a `@Configuration` with a `@Bean SecurityFilterChain` (Spring Security 6 style — NO `WebSecurityConfigurerAdapter`)
- Form login pointed at `/login`, default success URL `/`
- Logout pointed at `/logout`
- Public URLs: `/login`, `/signup`, `/css/**`, `/js/**` (for static assets)
- Admin URLs: `/ingest` requires hasRole("ADMIN")
- Everything else requires authentication
- BCrypt password encoder, strength 10
- A `UserDetailsService` implementation that loads from the `users` table

## Required code changes to existing files
- `ChatService` and `ChatController`: get the current user from `SecurityContextHolder` (or via `@AuthenticationPrincipal`) and pass `user_id` when persisting QA and Feedback rows
- The history query in `ChatController` filters by current user_id
- `index.html`: add a top bar with the current user's email and a "Logout" button (POST form)

## New files
- `User.java` (entity), `UserRepository.java`
- `SecurityConfig.java`
- `AuthController.java` for /signup, /login GET pages (form login POST is handled by Spring Security)
- `CustomUserDetailsService.java`
- `templates/login.html`, `templates/signup.html`
- `db/changelog/changes/002-add-users.sql`
- Update `db.changelog-master.yaml` to include the new changeset

## Conventions (unchanged from CLAUDE.md)
- Constructor injection only
- UUID primary keys
- Synchronous everywhere
- Plain Thymeleaf, no JS framework

## Required test
Add ONE integration test using Testcontainers + MockMvc that:
1. Signs up a new user
2. Confirms the user can GET /
3. Confirms an unauthenticated client gets redirected from / to /login
4. Confirms a USER role gets 403 on POST /ingest
5. Confirms an ADMIN role gets 200 on POST /ingest

Do not add other tests. The auth boundary is the one thing we MUST test.

## What "done" looks like
1. `docker compose down -v && docker compose up -d postgres` (clean slate)
2. `mvn spring-boot:run` — Liquibase applies V1 then 002
3. Visit / — redirected to /login
4. Click "Sign up" — make first account `admin@test.com` / `password123` — auto-logged in, redirected to /
5. See chat UI with "admin@test.com | Logout" in the top bar
6. Ingest works (admin role)
7. Ask a question, see answer with citations, see it in history
8. Logout, sign up second user `user@test.com`. Notice "Ingest" still visible but clicking returns 403 (this is fine for now)
9. Second user asks a different question. They see only their question in history, not the admin's
10. `mvn verify` passes
