# NoteTaking Service

A RESTful note-taking backend for small teams. Users can create personal notes, organize them into notebooks, and share individual notes with any team they belong to.

Built with **Spring Boot 3**, **H2** (file-based, swappable), and **JWT** authentication.

---

## Quick Start

**Prerequisites:** Java 21, Maven 3.9+

```bash
git clone https://github.com/ericmshort/NoteTaking.git
cd NoteTaking

# JWT_SECRET is required; generate one with: openssl rand -base64 64
export JWT_SECRET="$(openssl rand -base64 64)"

# Optional: bootstrap an admin user on first run
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=changeme

mvn spring-boot:run
```

The server starts on `http://localhost:8080`.  
Data is persisted in `./data/notetaking.mv.db` between restarts.  
H2 console: `http://localhost:8080/h2-console` — requires an ADMIN-role account.

---

## API Reference

All endpoints under `/api/v1/` require `Authorization: Bearer <token>` except auth routes.

### Auth

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/auth/register` | Create account, returns tokens |
| `POST` | `/api/v1/auth/login` | Log in, returns tokens (rate-limited: 5 failures/min per IP) |
| `POST` | `/api/v1/auth/refresh` | Exchange a refresh token for a new token pair |
| `POST` | `/api/v1/auth/logout` | Revoke the current access token and refresh token |

**Register / Login** both return:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "uuid-raw-value",
  "tokenType": "Bearer",
  "user": { "id": 1, "username": "alice", "email": "alice@example.com", ... }
}
```

**Refresh:**
```json
POST /api/v1/auth/refresh
{ "refreshToken": "uuid-raw-value" }
```
Returns a new token pair. Refresh tokens are **rotated on each use** — the old token is immediately revoked, so each token can only be used once.

**Logout:**
```json
POST /api/v1/auth/logout
Authorization: Bearer <accessToken>
{ "refreshToken": "uuid-raw-value" }   // body is optional but revokes the refresh token too
```

---

### Teams

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/teams` | List your teams |
| `POST` | `/api/v1/teams` | Create a team (you become OWNER) |
| `PUT` | `/api/v1/teams/{id}` | Rename/update team (OWNER only) |
| `DELETE` | `/api/v1/teams/{id}` | Delete team (OWNER only) |
| `GET` | `/api/v1/teams/{id}/members` | List team members |
| `POST` | `/api/v1/teams/{id}/members` | Add member by username (OWNER only) |
| `DELETE` | `/api/v1/teams/{id}/members/{userId}` | Remove member (OWNER only) |

**Create team:**
```json
POST /api/v1/teams
{ "name": "Engineering", "description": "Backend team" }
```

**Add member:**
```json
POST /api/v1/teams/1/members
{ "username": "bob" }
```

---

### Notebooks

Personal notebooks only. Notes can be organized into them optionally.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/notebooks` | List your notebooks |
| `POST` | `/api/v1/notebooks` | Create a notebook |
| `PUT` | `/api/v1/notebooks/{id}` | Rename notebook |
| `DELETE` | `/api/v1/notebooks/{id}` | Delete notebook |

---

### Notes

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/notes` | List notes visible to you (paginated) |
| `GET` | `/api/v1/notes?notebookId=1` | Filter by notebook |
| `GET` | `/api/v1/notes?q=meeting` | Full-text search (title + content) |
| `GET` | `/api/v1/notes?q=meeting&notebookId=1` | Search within a specific notebook |
| `GET` | `/api/v1/notes/{id}` | Get a note |
| `POST` | `/api/v1/notes` | Create a note |
| `PUT` | `/api/v1/notes/{id}` | Update a note (owner only) |
| `DELETE` | `/api/v1/notes/{id}` | Delete a note (owner only) |

Pagination parameters: `?page=0&size=20` (defaults: page 0, size 20).

**Create note (private):**
```json
POST /api/v1/notes
{ "title": "Meeting notes", "content": "..." }
```

**Create note shared with a team:**
```json
POST /api/v1/notes
{
  "title": "Sprint planning",
  "content": "...",
  "notebookId": 2,
  "sharedWithTeamId": 1
}
```

**Share an existing note with a team (or unshare):**
```json
PUT /api/v1/notes/5
{ "sharedWithTeamId": 1 }   // share with team 1
{ "sharedWithTeamId": -1 }  // unshare (make private)
```

**Note visibility rules:**
- Notes are **private by default** (only the owner sees them)
- Setting `sharedWithTeamId` makes the note visible to all members of that team
- You can only share a note with a team you belong to
- Only the note owner can edit, archive, or delete it

**List response:**
```json
{
  "content": [ { "id": 1, "title": "...", "ownerId": 1, "sharedWithTeamId": null, ... } ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "last": false
}
```

---

### Admin (requires `ADMIN` role)

| Method | Path | Description |
|--------|------|-------------|
| `PUT` | `/api/v1/admin/users/{username}/role` | Promote or demote a user's role |
| `DELETE` | `/api/v1/admin/users/{username}/tokens` | Force-revoke all tokens for a user (logout all sessions) |

**Set role:**
```json
PUT /api/v1/admin/users/bob/role
{ "role": "ADMIN" }
```

Valid roles: `USER`, `ADMIN`. Changing a user's role immediately invalidates all their existing tokens.

---

## Security

### Authentication
All `/api/v1/` endpoints except `/auth/register` and `/auth/login` require `Authorization: Bearer <token>`.

### HTTPS Enforcement
Set `HTTPS_ENFORCE=true` to redirect all plain HTTP requests to HTTPS. The service reads the `X-Forwarded-Proto` header from reverse proxies via `server.forward-headers-strategy=framework`.

### CORS
Allowed origins are configured via the `CORS_ALLOWED_ORIGINS` environment variable (comma-separated). Default: `http://localhost:3000`.

```bash
export CORS_ALLOWED_ORIGINS="https://app.example.com,https://staging.example.com"
```

### Login Rate Limiting
The `POST /api/v1/auth/login` endpoint is limited to **5 attempts per minute per IP address** (sliding window). Exceeding this returns `429 Too Many Requests`.

### Refresh Tokens
Access tokens expire after 15 minutes. The login and register responses include a `refreshToken` (7-day lifetime). Exchange it at `POST /api/v1/auth/refresh` for a new access + refresh token pair. Refresh tokens are **rotated on every use** — the old token is revoked the moment a new one is issued, so a stolen token can only be used once before it's detected.

Refresh tokens are stored as SHA-256 hashes in the database. The raw value is sent to the client and never persisted.

### Account Lockout
After **10 consecutive failed login attempts** the account is locked for **15 minutes**. The thresholds are configurable:
```properties
app.security.max-failed-attempts=10
app.security.lockout-minutes=15
```
A successful login resets the counter.

### Token Revocation
- **Self-logout:** `POST /api/v1/auth/logout` immediately revokes the calling user's access token (by JTI) and their refresh token.
- **Admin force-logout:** `DELETE /api/v1/admin/users/{username}/tokens` revokes all tokens for a user by recording a revocation timestamp — any access token issued before that moment is rejected, and all stored refresh tokens for the user are marked revoked.
- **Revocation store:** defaults to an in-memory store (entries survive until JVM restart; a scheduled task purges expired entries hourly). Activate the **Redis-backed store** by setting `spring.data.redis.host` — no code changes needed, the implementation is selected automatically via `@ConditionalOnProperty`.

### Admin Role
Users have one of two roles: `USER` (default) or `ADMIN`. Admin-only surfaces:
- `/api/v1/admin/**` endpoints
- `/h2-console/**` (H2 database console)
- `/actuator/**` (except `/actuator/health` and `/actuator/info`, which are public)

The first admin account is bootstrapped via `ADMIN_USERNAME` / `ADMIN_PASSWORD` environment variables on startup.

---

## Localization

Error messages are localized. Set the `Accept-Language` header to select a locale:

| Header value | Language |
|---|---|
| `en` | English (default) |
| `fr` | French |
| `es` | Spanish |
| `it` | Italian |

---

## Swapping the Database

The service uses **H2** (file-based) by default. To migrate to PostgreSQL or MySQL:

1. Replace the H2 dependency in `pom.xml` with the target driver
2. Update `application.properties`:

```properties
# PostgreSQL example
spring.datasource.url=jdbc:postgresql://localhost:5432/notetaking
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
```

3. Run `ddl-auto=create` once to generate the schema, then switch to `validate`.

No Java code changes are required — all data access goes through Spring Data JPA repositories.

---

## Configuration

All secrets are injected via environment variables. No secrets are hardcoded.

| Environment variable | Required | Default | Description |
|---|---|---|---|
| `JWT_SECRET` | **Yes** | — | Base64-encoded signing key; generate: `openssl rand -base64 64` |
| `ADMIN_USERNAME` | No | — | Bootstrap admin username (created on first startup if missing) |
| `ADMIN_PASSWORD` | No | — | Bootstrap admin password |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000` | Comma-separated allowed CORS origins |
| `HTTPS_ENFORCE` | No | `false` | Redirect HTTP→HTTPS (enable behind a TLS proxy) |

**Production checklist:**
- Set `JWT_SECRET` to a securely generated key
- Set `HTTPS_ENFORCE=true` with a TLS-terminating reverse proxy (also enables HSTS)
- Set `CORS_ALLOWED_ORIGINS` to your frontend's domain
- Set `spring.jpa.hibernate.ddl-auto=validate` after initial schema creation
- Set `spring.data.redis.host` to activate the Redis-backed token revocation store
- Consider disabling the H2 console: `spring.h2.console.enabled=false` (it's already ADMIN-only when enabled)

---

## Project Structure

```
src/main/java/com/notetaking/
├── NoteTakingApplication.java
├── config/          SecurityConfig, DataInitializer
├── controller/      AuthController, AdminController, NoteController, NotebookController, TeamController
├── dto/             request/ and response/ DTOs
├── entity/          User (with Role enum), Team, TeamMembership, Notebook, Note, RefreshToken
├── exception/       AppException, GlobalExceptionHandler (RFC 7807-style errors)
├── repository/      Spring Data JPA interfaces
├── security/        JwtTokenProvider, JwtAuthenticationFilter, LoginRateLimitFilter, UserDetailsServiceImpl
├── service/         AuthService, AdminService, AccountLockService, RefreshTokenService,
│                    TokenRevocationService, InMemoryTokenRevocationStore, RedisTokenRevocationStore,
│                    NoteService, NotebookService, TeamService
└── validation/      NullOrNotBlank (custom constraint)
```

---

## Design Decisions

### Why H2 instead of Derby?
Apache Derby has significant compatibility issues with Hibernate 6 (Spring Boot 3's ORM) and Java 21's module system. H2 in file mode provides the same embedded, zero-install experience Derby was chosen for, with full Hibernate 6 support and an included web console for inspecting data during development. The swap path to a production database is identical either way — just change the driver and URL in `application.properties`.

### Why explicit note sharing instead of team-owned notebooks?
An earlier design considered making notebooks team-scoped (all notes in a team notebook automatically visible to the team). The explicit sharing model — where notes are private by default and owners choose which team to share with — was chosen instead because it matches how small teams actually work: you draft something privately, then share it when it's ready. Automatic sharing on notebook membership would surface work-in-progress notes to the whole team without intent.

### Why JWT over sessions?
The service is stateless by design to make it easy to run multiple instances behind a load balancer later without sticky sessions or a shared session store. JWTs are validated entirely from the token, with no server-side state required.

### Why a single `sharedWithTeamId` per note instead of many-to-many?
A note shared with multiple teams simultaneously adds query complexity and cross-team information leakage risk without a clear use case for small teams. A single team share keeps the access model simple and auditable. The schema can be extended to many-to-many later by adding a `note_team_shares` join table.

### Why layered architecture (Controller → Service → Repository)?
Each layer has one responsibility and is independently testable. Controllers are mocked at the service boundary; services are mocked at the repository boundary. This also means the HTTP transport and the database can both be swapped without touching business logic.

### Why Spring Data JPA repositories instead of raw SQL?
All queries are expressed in JPQL against entity objects, not table names or column names. When the database is swapped, the query syntax remains valid — only the dialect and driver change. The one exception is the LIKE-based full-text search, which is explicitly documented as a placeholder for a DB-native search index.

---
### Personal Design Decisions
I chose to write the project in Java as I am most familiar with that language.  I also chose to use a simple file based H2 db to start with, but to make it easy to swap in a production grade db at a later time.  Since no database preference was mentioned, I decided to make it easy to install any db easily when put into production.  There are areas to be improved to make this project into a production ready backend server.  I have tried to cover the security functionality so that it would start out very secure.  This area is the most important area for a server to focus on.

---

## If We Had More Time

### Production-readiness gaps

- **Schema migrations (Flyway)** — `ddl-auto=update` is acceptable in development but dangerous in production: it cannot drop columns or tables and can silently fail on constraint changes. Versioned Flyway migration scripts would make schema evolution auditable, reversible, and safe to run in CI before deploy.

- **PostgreSQL migration** — H2 is intentionally used here as a zero-config embedded database suited to a self-contained exercise. The switch to PostgreSQL (or any JDBC-compatible DB) requires changing three `application.properties` lines and swapping the Maven dependency; no Java changes are needed. I would do this before any production deployment.

- **Testcontainers integration** — the `@SpringBootTest` integration tests run against H2. Running the same tests against a real PostgreSQL instance via Testcontainers would catch dialect-specific query issues before they reach production.

- **Per-user rate limiting** — the login rate limit is per source IP. Behind a corporate NAT or shared WiFi, all users share the same budget. Combining per-IP and per-username limiting would be more precise while still blocking distributed credential-stuffing from many IPs.

### Code quality improvements

- **Centralize `resolveId`** — `NoteController`, `NotebookController`, and `TeamController` each contain an identical private `resolveId(username)` helper that looks up the authenticated user. Extracting this into a `CurrentUserService` (or a custom `@AuthenticationPrincipal` resolver) would eliminate the duplication and the extra database round-trip on every request.

- **OpenAPI / Swagger UI** — adding `springdoc-openapi` would auto-generate interactive API documentation from the existing controller annotations at zero maintenance cost.

### Feature additions

- **Role-based note permissions within teams** — all team members currently have equal read access to shared notes. A finer-grained model (view-only vs. editor) would suit larger teams.
- **Multi-team sharing** — replace the single `sharedWithTeamId` with a `note_team_shares` join table.
- **Note tagging** — a many-to-many `tags` relationship enabling tag-based filtering alongside notebook organization.
- **Soft delete / trash** — notes currently hard-delete. A `deleted_at` timestamp with a restore API would prevent accidental loss.
- **Search index** — the LIKE-based search is O(n) across all visible notes. A PostgreSQL `tsvector` index or Elasticsearch integration would scale to large data sets. The service-layer search interface is already isolated for this swap.
- **Audit log** — record who created, edited, shared, or deleted each note to support compliance requirements.
- **Email verification** — confirm addresses at registration before allowing login.

---

## Running Tests

```bash
mvn test
```

55 tests across unit, slice, and full-stack integration layers:

| Layer | Test class | Count |
|---|---|---|
| Unit — services | `AuthServiceTest`, `NoteServiceTest`, `AccountLockServiceTest`, `RefreshTokenServiceTest`, `InMemoryTokenRevocationStoreTest`, `AdminServiceTest` | 29 |
| Controller slice (`@WebMvcTest`) | `AuthControllerTest`, `NoteControllerTest` | 7 |
| Full-stack integration (`@SpringBootTest`) | `AuthIntegrationTest` | 13 |
| Application context | `NoteTakingApplicationTests` | 1 |
| **Total** | | **55** |

The integration tests exercise the full HTTP → Security → Controller → Service → H2 stack with no mocks.

---

## Building a Runnable JAR

```bash
mvn package -DskipTests
java -jar target/notetaking-service-1.0.0-SNAPSHOT.jar
```
