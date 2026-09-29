# NoteTaker5000

A lightweight, spec-first note-taking service shared amongst small teams. Users capture
their work as **notes**, **save**/**update** them, mark them **complete**, and **share**
notes with other users. Every note is owned by the authenticated user (identified by the
`employeeNumber` claim on their JWT) and only the owner may modify or delete it.

This README doubles as the living **project plan**. Deep-dive docs live in [`docs/`](docs/).

---

## 1. Goals & scope (v1)

Start basic, build the spine correctly:

- **Create** a note (owned by the caller).
- **Read** a single note / **list** notes visible to the caller (owned + shared-with-me).
- **Update** a note — **owner only** (enforced via the JWT `employeeNumber`).
- **Complete** a note — mark it done and save it (owner only).
- **Delete** a note — owner only.
- **Share** a note with another user (READ/WRITE) and **unshare** — owner only.

Deliberately out of scope for v1 (noted for later): rich text/attachments, team/group
objects, full-text search, soft-delete/audit history, outbound service integrations.

## 2. Tech stack

| Concern            | Choice                                                             |
|--------------------|-------------------------------------------------------------------|
| Language / runtime | **Java 21**                                                       |
| Framework          | **Spring Boot 3.5.x** (Web MVC, Data JPA, Actuator, Validation)   |
| API                | **OpenAPI-first** via `org.openapi.generator` (`spring`, delegate pattern) + springdoc Swagger UI |
| Persistence        | **Hibernate/JPA**; HikariCP                                        |
| Database           | **H2** (in-memory, Oracle-compat) locally · **Oracle** for integration/prod |
| Migrations         | **Liquibase** (SQL changelogs; PKs & indexes called out)          |
| Security           | **OAuth2 Resource Server**, multi-issuer JWT (OKTA FIT-UI + self/bypass issuer) |
| Token/JWKS cache   | **Nimbus** cached, refresh-ahead JWKS + **Caffeine**              |
| Mapping/boilerplate| **MapStruct** + **Lombok**                                        |

## 3. Runtime profiles & database strategy

Mirrors the reference worklist service:

- **`local` / `component-test`** → H2 in-memory in Oracle mode
  (`jdbc:h2:mem:...;MODE=Oracle`), **Liquibase enabled**, H2 console on, config server off.
- **default (integration/prod)** → **Oracle** via Hikari, `OracleDialect`,
  Liquibase managed by the deployment pipeline (`spring.liquibase.enabled=false` in-app).

The same JPA entities and Liquibase changelogs run against both databases, so local H2
behaves like Oracle (identifiers, ordering, types).

## 4. Security model

OAuth2 **resource server** validating **Bearer JWTs** from multiple trusted issuers via
`JwtIssuerAuthenticationManagerResolver`:

1. **FIT-UI OKTA issuer** — real user tokens coming from the UI (same pattern the worklist
   service uses to consume FIT-UI's token). The caller's identity is taken from the
   **`employeeNumber`** claim and stored as the note `ownerId`.
2. **Self / bypass issuer** — our own issuer so we can mint tokens and run tests without OKTA.

Public URIs (`/actuator/health`, `/actuator/info`, Swagger, `/h2-console`) are permitted;
everything else requires a valid JWT. See [`docs/SECURITY.md`](docs/SECURITY.md).

### Token / "keychain" caching
Following the Ship Measurement Service (SMS) reference, we do **not** re-fetch or re-validate
signing keys on every request. A cached, **refresh-ahead, outage-tolerant JWKS source**
(Nimbus `JWKSourceBuilder`) backs each issuer's `JwtDecoder`, and Caffeine is wired for
response/data caching. Note: SMS caches **JWKS public keys** (validation material), not raw
bearer tokens — the same approach is used here. A true validated-token cache can be layered
on later if needed.

## 5. Database performance (Oracle)

The app must stay fast on Oracle. Keys/indexes are declared explicitly in the Liquibase
changelogs (not left implicit):

- `NOTE` — PK on `NOTE_ID`; index on `OWNER_ID` (list-my-notes); index on `UPDATED_TS`
  (ordering / recent-first paging).
- `NOTE_SHARE` — composite PK `(NOTE_ID, SHARED_WITH_USER_ID)`; index on
  `SHARED_WITH_USER_ID` (find notes-shared-with-me); FK `NOTE_ID → NOTE(NOTE_ID)`.

See [`docs/DATA-MODEL.md`](docs/DATA-MODEL.md).

## 6. API surface (v1)

| Method | Path                                   | Operation        | Access        |
|--------|----------------------------------------|------------------|---------------|
| POST   | `/api/v1/notes`                        | `createNote`     | any user      |
| GET    | `/api/v1/notes`                        | `listNotes`      | any user      |
| GET    | `/api/v1/notes/{id}`                    | `getNote`        | owner/shared  |
| PUT    | `/api/v1/notes/{id}`                    | `updateNote`     | owner         |
| POST   | `/api/v1/notes/{id}/complete`          | `completeNote`   | owner         |
| DELETE | `/api/v1/notes/{id}`                   | `deleteNote`     | owner         |
| GET    | `/api/v1/notes/{id}/shares`            | `listNoteShares` | owner         |
| POST   | `/api/v1/notes/{id}/shares`            | `shareNote`      | owner         |
| DELETE | `/api/v1/notes/{id}/shares/{userId}`   | `unshareNote`    | owner         |

The OpenAPI spec (with request/response examples) lives in [`openapi/`](openapi/). How the
spec is built and **how to extend the API** is documented in [`docs/OPENAPI.md`](docs/OPENAPI.md).

## 7. Building & running

Requires **JDK 21** (`JAVA_HOME` → a JDK 21).

```bash
# Generate API sources + compile + test
./gradlew build

# Run locally on H2 (Liquibase creates the schema, H2 console at /h2-console)
./gradlew bootRun --args='--spring.profiles.active=local'
```

Swagger UI: `http://localhost:8080/swagger-ui.html` · OpenAPI JSON: `/v3/api-docs`.

## 8. Repository

```bash
git init
git add .
git commit -m "Initial NoteTaker5000 scaffold"
git remote add origin https://github.com/1671437_fedex/noteTaker5000.git
```

(Push is intentionally left to a human; nothing is pushed automatically.)

## 9. Plan / task tracking

Implementation is split across the packages described in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
Status of the initial build-out is tracked in [`docs/PLAN.md`](docs/PLAN.md).
