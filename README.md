# NoteTaking Service

A RESTful note-taking backend for small teams. Users can create personal notes, organize them into notebooks, and share individual notes with any team they belong to.

Built with **Spring Boot 3**, **H2** (file-based, swappable), and **JWT** authentication.

---

## Quick Start

**Prerequisites:** Java 21, Maven 3.9+

```bash
git clone https://github.com/ericmshort/NoteTaking.git
cd NoteTaking
mvn spring-boot:run
```

The server starts on `http://localhost:8080`.  
Data is persisted in `./data/notetaking.mv.db` between restarts.  
H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/notetaking`)

---

## API Reference

All endpoints under `/api/v1/` require `Authorization: Bearer <token>` except auth routes.

### Auth

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/auth/register` | Create account, returns JWT |
| `POST` | `/api/v1/auth/login` | Log in, returns JWT |

**Register:**
```json
POST /api/v1/auth/register
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "password123",
  "displayName": "Alice"
}
```

**Login:**
```json
POST /api/v1/auth/login
{ "username": "alice", "password": "password123" }
```

Both return:
```json
{ "token": "...", "tokenType": "Bearer", "user": { "id": 1, "username": "alice", ... } }
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
| `GET` | `/api/v1/notes?q=meeting` | Full-text search |
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

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | H2 file path | Database URL |
| `app.jwt.secret` | (see below) | Base64-encoded HMAC-SHA256 key |
| `app.jwt.expiration-ms` | `86400000` (24h) | Token lifetime |

**Production checklist:**
- Replace `app.jwt.secret` with a securely generated 256-bit key:
  ```bash
  openssl rand -base64 64
  ```
- Set `spring.jpa.show-sql=false`
- Remove or restrict the H2 console: `spring.h2.console.enabled=false`
- Set `spring.jpa.hibernate.ddl-auto=validate` after the schema is created

---

## Project Structure

```
src/main/java/com/notetaking/
├── NoteTakingApplication.java
├── config/          SecurityConfig
├── controller/      AuthController, NoteController, NotebookController, TeamController
├── dto/             request/ and response/ DTOs
├── entity/          User, Team, TeamMembership, Notebook, Note
├── exception/       AppException, GlobalExceptionHandler (RFC 7807-style errors)
├── repository/      Spring Data JPA interfaces
├── security/        JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl
└── service/         AuthService, NoteService, NotebookService, TeamService
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

> **Your notes here** — add anything specific about the choices you made, constraints you were working within, or tradeoffs you consciously accepted.

---

## If We Had More Time

These features were consciously left out of scope but represent natural next steps:

- **Flyway schema migrations** — currently using `ddl-auto=update` (fine for dev, risky in production). Flyway versioned migration scripts would make schema changes auditable and reversible.
- **Role-based note permissions within teams** — right now all team members have equal read access to shared notes. A finer-grained model (read-only vs. editor per team member) would suit larger teams.
- **Note tagging** — a many-to-many `tags` relationship on notes enabling tag-based filtering alongside notebook-based organization.
- **Sharing with multiple teams** — replace the single `sharedWithTeamId` with a `note_team_shares` join table so a note can be shared with more than one team simultaneously.
- **Soft delete / trash** — notes currently hard-delete. A `deleted_at` timestamp with a trash/restore API would prevent accidental permanent loss.
- **Search index integration** — the current LIKE-based search is O(n) across all visible notes. A PostgreSQL `tsvector` index or Elasticsearch integration would scale to large note sets. The service layer search interface is already isolated for this replacement.
- **Audit log** — record who created, edited, shared, or deleted each note, with timestamps, to support compliance and accountability requirements.
- **Rate limiting** — per-user request throttling on the API to prevent abuse; straightforward to add as a Spring filter with a token-bucket implementation.
- **Refresh tokens** — the current JWT is a single long-lived token (24h). Issuing a short-lived access token + long-lived refresh token improves security by limiting the blast radius of a leaked token.
- **OpenAPI / Swagger UI** — auto-generate interactive API docs from the controller annotations using `springdoc-openapi`.
- **Email verification** — confirm user email addresses at registration before allowing login.
- **Integration test suite** — the current tests mock the database layer. A full `@SpringBootTest` integration suite running against a real (Testcontainers) PostgreSQL instance would catch dialect-specific query issues before they reach production.

---

## Running Tests

```bash
mvn test
```

16 tests covering service-layer logic and controller endpoints.

---

## Building a Runnable JAR

```bash
mvn package -DskipTests
java -jar target/notetaking-service-1.0.0-SNAPSHOT.jar
```
