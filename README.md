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
