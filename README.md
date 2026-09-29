# NoteTaker5000

A lightweight note-taking service for small teams. People capture their work as **notes**
inside shared **workspaces**, edit them, mark them **complete**, and **trash/restore** them.
Who can do what is governed by each member's **role** in a workspace (OWNER / EDITOR / VIEWER).

Callers are identified by the `employeeNumber` on their JWT and provisioned automatically on
first use — there is no separate sign-up step.

So the initial tech stack I wanted to clearly define rather than leaving it up to AI to go off on as I wanted to be clear what each was doing. Having a highly performant application is all great until someone goes to maintain any piece of the code and its completely and utterly unreadable. For this application I didn't feel like we needed to reach to dramatically different package structure like what can be found in Hexagonal Arch'd applications as its fairly flat, there's no other secondary jobs or processes running or external services getting invoked that would make breaking it out in ports and adapters and whatnot clearer. The main reasoning behind the selected dependencies is also to just keep the boiler plate code as lean as possible. Business code should be the priority of most developers (imo) and to re-plumb pipes every time is something id rather not have developers be spending their time doing...especially when it can all be done slightly different from one another causing diagnosing issues harder as you have no solid base to use as a reference.

The security profile was probably second but I only know through experience with OKTA that it has a tendency to validate each and every token. So not only would clients being going to issue and get minted new tokens over and over (which has a direct cost) but also the server (the service the client is calling) has to then validate each and every time as well. Coming to some sort of agreement for how long tokens should be valid for would also cover us for outage times. 4hrs is safer and just overall reduces the amount of calls the server has to call OKTA to validate and instead just keeps a keychain it can reference instead. This might be wrong for high user count applications as holding all of those would be bad but for small groups of people...

The 'collaboration' workplace model was the hardest feature to properly define. Treat shared areas as spaces/groups, rather than just individualized, forced a full redo of the application. It went from per-note sharing to Workspace + WorkspaceNum, grants and roles in almost every operation. Ensuring that the component tests had some flows that not only covered just simple endpoint articulations but also scenarios with realistic multi user, seeding pre-existing data via repo/H2 was the last thing to add and get covered.

As far as what I'd change, add, or stop doing if I had more time - I mean its the best notes app with no UI - there's nothing it cannot do. 
I think as far as features to be added down the road I mentioned on the testGorilla site but the largest would be actually sync'd working on the same note at the same time. It would be a much larger tech ask but would be a quality of life improvement. Just really depends on users input as to if it's necessary/useful. The original premise of this application was that 'several small teams' so even right now this application is more than likely larger than what the real ask is.

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
