# NoteTaker5000

A lightweight note-taking service for small teams. People capture their work as **notes**
inside shared **workspaces**, edit them, mark them **complete**, and **trash/restore** them.
Who can do what is governed by each member's **role** in a workspace (OWNER / EDITOR / VIEWER).

Callers are identified by the `employeeNumber` on their JWT and provisioned automatically on
first use — there is no separate sign-up step.

---

## What you can do with it

- **Organize by workspace.** A workspace is a shared space/group that owns notes. A user only
  ever sees notes in workspaces they belong to.
- **Collaborate by role.** Add people to a workspace as `OWNER`, `EDITOR`, or `VIEWER`:
  - `OWNER` — full control, including managing members.
  - `EDITOR` — create, edit, complete, trash and restore notes.
  - `VIEWER` — read-only access to the workspace's notes.
- **Manage notes.** Create, read, list, search-by-title, update, and mark notes complete.
- **Trash & restore.** Delete is a soft-delete to a per-workspace trash; notes can be listed
  from the trash and restored.
- **Safe concurrent edits.** Notes carry a version; a stale save is rejected (HTTP 409) rather
  than silently overwriting someone else's change.

Future features (folders, tags, version history, real-time co-editing, …) are tracked in
[`docs/ROADMAP.md`](docs/ROADMAP.md).

---

## Using the API

The API is the product surface a client (e.g. a NoteTaker UI) integrates against. Every call
except public URIs needs `Authorization: Bearer <JWT>`; the caller is read from the token's
`employeeNumber` claim. All ids (`id`, `workspaceId`, `userId`) are numeric.

The full contract, with request/response examples, lives in [`openapi/`](openapi/) and is
served as **Swagger UI** at `/swagger-ui.html`.

### Workspaces & members

| Method | Path                                        | Purpose                                            | Who         |
|--------|---------------------------------------------|----------------------------------------------------|-------------|
| POST   | `/api/v1/workspaces`                        | Create a workspace; caller becomes its first OWNER | any user    |
| GET    | `/api/v1/workspaces`                        | List the workspaces the caller belongs to (with their role) | any user |
| GET    | `/api/v1/workspaces/{id}/members`           | List a workspace's members                         | member      |
| POST   | `/api/v1/workspaces/{id}/members`           | Add a member by `employeeNumber` + role, or update an existing member's role | OWNER |
| DELETE | `/api/v1/workspaces/{id}/members/{userId}`  | Remove a member (the last OWNER cannot be removed) | OWNER       |

Adding a member returns `201` when the member is new and `200` when an existing member's role
is updated. Members added by `employeeNumber` are provisioned automatically if they have never
signed in.

### Notes

| Method | Path                          | Purpose                                          | Who          |
|--------|-------------------------------|--------------------------------------------------|--------------|
| POST   | `/api/v1/notes`               | Create a note in a workspace                     | OWNER/EDITOR |
| GET    | `/api/v1/notes`               | List active notes (paged; optional `workspaceId`, `searchTerm`) | member |
| GET    | `/api/v1/notes/trash`         | List soft-deleted notes (the trash)              | member       |
| GET    | `/api/v1/notes/{id}`          | Read a single note                               | member       |
| PUT    | `/api/v1/notes/{id}`          | Update title/content and optionally completed    | OWNER/EDITOR |
| POST   | `/api/v1/notes/{id}/complete` | Mark a note complete                             | OWNER/EDITOR |
| POST   | `/api/v1/notes/{id}/restore`  | Restore a note from the trash                    | OWNER/EDITOR |
| DELETE | `/api/v1/notes/{id}`          | Soft-delete a note to the trash                  | OWNER/EDITOR |

`listNotes` returns newest-first and is paged via `pageNumber` (1-based, default 1) and
`itemsPerPage` (default 20, max 200), alongside a `pageInfo` with `pagesCount` and
`totalItems`. `searchTerm` filters case-insensitively on the title.

### Payload shapes

- **`Note`** — `{ id, workspaceId, title, content?, completed, createdByUserId, createdAt,
  updatedAt, deletedAt? }`. `deletedAt` is absent while the note is active.
- **`Workspace`** — `{ id, name, role, createdAt, updatedAt }`, where `role` is *your* role in
  that workspace.
- **Errors** — a consistent `ErrorResponse` body with the matching status: `400` invalid
  request, `401` missing/invalid token, `403` insufficient role, `404` not found or not
  visible, `409` concurrent-edit conflict.

---

## For developers

### Tech stack

| Concern            | Choice                                                                      |
|--------------------|-----------------------------------------------------------------------------|
| Language / runtime | **Java 21**                                                                 |
| Framework          | **Spring Boot 3.5** (Web MVC, Data JPA, Actuator, Validation)               |
| API                | **OpenAPI-first** via `org.openapi.generator` (Spring delegate) + Swagger UI |
| Persistence        | **Hibernate/JPA**, HikariCP; **Liquibase** migrations (explicit PKs/indexes) |
| Database           | **H2** (in-memory, Oracle mode) locally · **Oracle** when deployed          |
| Security           | **OAuth2 resource server**, multi-issuer JWT with cached refresh-ahead JWKS + a validated-token keychain |
| Mapping            | **MapStruct** + **Lombok**                                                  |
| Tests              | **Spock** unit specs + a component-test package that boots the app over HTTP |

### Run & test

Requires **JDK 21** (`JAVA_HOME` → a JDK 21).

```bash
# Generate API sources, compile, and run all tests
./gradlew build

# Run locally on H2 (Liquibase creates the schema; H2 console at /h2-console)
./gradlew bootRun --args='--spring.profiles.active=local'
```

Swagger UI: `http://localhost:8080/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`.

### Profiles & auth

- **`local` / `component-test`** → H2 in Oracle mode, Liquibase enabled, **JWT validation
  bypassed**. Impersonate a user with the `X-Employee-Number: <id>` header — handy for local
  UI development and tests.
- **default (deployed)** → Oracle datasource; JWTs are validated against the configured
  issuer(s), and the caller is taken from the `employeeNumber` claim.

### Data model

Four tables in schema `NOTETAKER` (identity PKs, explicit indexes, corporate DANSAC naming):

- `APP_USER` — a provisioned user (`USER_ID`, unique `EMP_NBR`).
- `WORKSPACE` — a shared space that owns notes.
- `WORKSPACE_MEMBER` — a user's membership + `ROLE_CD` in a workspace (unique per user+workspace).
- `NOTE` — belongs to a workspace; `COMPLETED_FLG`, optimistic-lock `VERS_NBR`, soft-delete
  `DEL_TMSTP` (null = active).