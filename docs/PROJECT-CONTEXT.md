# NoteTaker5000 — Project Context & Development Status

> **Purpose of this file:** A durable snapshot of all working context for this project so
> development can be resumed after a workspace/IDE reset or folder move. Captures the reference
> apps used, key decisions, what has been built, the current blocker, and the remaining work.
>
> **Last updated:** 2026-09-28
> **Current folder:** `C:\Users\752572\IdeaProjects\noteTaker_5000` (moved from the original `...\FIT`)

---

## 1. What this project is

**NoteTaker5000** — a note-taking service used and shared amongst several small teams. Users
capture their work as notes. It is an **OpenAPI-first, Spring Boot 3.5 / Java 21** service.

- **This is a demo only.** No CI/CD, no deployment pipeline, no FedEx-internal infra required.
- **Base package:** `com.notetaker` · **Group:** `com.notetaker` · **Artifact:** `note-taker-service`
- **Root Gradle project:** `noteTaker5000`
- **Target GitHub remote (do NOT push without explicit approval):**
  `https://github.com/1671437_fedex/noteTaker5000.git`

### Core features (agreed scope)
- Users **write / save** notes.
- Users **update** notes — **only their own** (ownership enforced via JWT `employeeNumber`).
- Users **complete** a note (and save it).
- Users **delete** their own notes.
- **Share notes** between users (read/write permission).
- Persistence via **Hibernate / JPA** entities.
- **Performant on Oracle** — primary keys and indexes are declared explicitly in Liquibase changelogs.

---

## 2. Reference applications used

These local projects were used as scaffolding/pattern references (NOT dependencies):

| Reference app | Path | What we took from it |
|---|---|---|
| **FXF FIT Worklist Service** | `C:\Users\752572\IdeaProjects\eai-3539948-fxf-fit-worklist-service` | Package structure, Gradle dependency setup, **OpenAPI spec-first + delegate pattern**, how the API is built and extended, Liquibase changelog layout, security config shape, `application.yml` / `application-local.yml` split, JWT `employeeNumber` extraction (their `EmployeeDataService`). |
| **FXF Ship Measurement Service (SMS)** | `C:\Users\752572\IdeaProjects\eai-3531268-fxf_ship_measurement_service` | **Token/JWKS caching pattern.** SMS caches **JWKS public keys** (Nimbus `JWKSourceBuilder` with refresh-ahead + outage tolerance in its `CachedJwksJwtDecoderConfig`), NOT raw tokens. Also Caffeine for response caching, and multi-issuer via `JwtIssuerAuthenticationManagerResolver`. |

> Note: The user referenced "caching tokens in a keychain like SMS." In practice SMS caches the
> **JWKS signing keys** (so it doesn't re-fetch/re-validate the key material on every request), which
> is the pattern implemented here. See `docs/SECURITY.md`.

---

## 3. Key decisions made (with rationale)

1. **Lean, standard-Spring dependency footprint** — no FedEx-internal Nexus artifacts, no JMS, no
   config-server. Chosen so the demo builds/runs anywhere.
2. **Generic, non-FedEx naming** — base package `com.notetaker`, etc.
3. **Two run modes:**
   - **Local / component-test:** **H2 in-memory** database (Oracle-compatibility mode), Liquibase runs in-app.
   - **Deployed (default profile):** **Oracle** datasource (via env vars), OracleDialect, schema `NOTETAKER`.
4. **Security (simplified for demo):**
   - **`local` and `component-test` profiles require NO JWT** (permit-all filter chain) so the app and
     tests can run without a token.
   - **Other/deployed profiles** require the **OKTA UI-issued JWT** (multi-issuer resource server).
   - `application-local.yml` uses **fake placeholder issuer URLs** (`notetaker-ui-issuer`,
     `notetaker-app-issuer`) — not real endpoints.
5. **OpenAPI-first** — the `openapi/` spec is the source of truth; the Spring delegate interfaces are
   generated; we hand-write the delegate implementation (controller) + service + mapper.
6. **Performance** — explicit PK/index declarations in Liquibase changelogs (see `docs/DATA-MODEL.md`).
7. **No CI** — `.github/` and `manifest.yml` were intentionally removed.

---

## 4. Current status

### Done
- [x] Gradle scaffold: `settings.gradle`, `gradle.properties`, `build.gradle`, `.gitignore`, Gradle wrapper 8.5.
- [x] **OpenAPI spec** (`openapi/`) — CRUD + complete + shares, with examples. **`openApiGenerate` succeeds.**
- [x] Docs: `README.md` (doubles as the plan) + `docs/{ARCHITECTURE,OPENAPI,DATA-MODEL,SECURITY,PLAN}.md`.
- [x] **Persistence slice (A):** entities, repositories, `application.yml`, `application-local.yml`,
      `logback.xml`, Liquibase changelogs (note + note_share tables, keys/indexes).
- [x] **Security slice (B):** `SecurityConfig`, `CachedJwtDecoderFactory`, `CacheConfig`,
      `CurrentUserService(+Impl)`, `SecurityProperties`, `UserIdMissingException`.
- [x] **Service/Controller slice (C):** `NoteService(+Impl)`, `NoteMapper`, `NoteController`,
      note exceptions, `GlobalExceptionHandler`.
- [x] Security simplification applied (local = no JWT; header/`default-user-id` fallback for owner id).
- [x] CI/manifest removed (demo only).
- [x] All three background coding agents completed.

### Blocked / not yet done
- [ ] **`compileJava` FAILS — "invalid source release: 21".** **ROOT CAUSE IDENTIFIED (see §5).**
      Build work is **paused at the user's request** until the project is restructured/moved.
- [ ] Integration pass to reconcile the three agent slices (MapStruct enum mapping, repository method
      names, `ErrorResponse` builder usage).
- [ ] Tests (slice D): notes end-to-end on H2 (local, no JWT), owner-only enforcement via
      `X-Employee-Number` header, share/unshare.
- [ ] Full `./gradlew build` green on JDK 21.
- [ ] Update `docs/SECURITY.md` to reflect the simplified "local = no JWT" bypass (still describes the
      older test-issuer approach).
- [ ] `git init` + commit + `git remote add origin <repo>` (do NOT push without approval; include
      Co-authored-by trailer).

---

## 5. The build blocker — root cause & fix

**Symptom:** `./gradlew compileJava` → `error: invalid source release: 21`.

**Root cause (confirmed via `--info`):** Gradle selects a **Java 17 toolchain** for compilation:
```
Compiling with toolchain 'C:\Users\752572\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot'.
> error: invalid source release: 21
```
Even though the Gradle daemon itself runs on JVM 21.0.10, `build.gradle` uses
`sourceCompatibility/targetCompatibility = JavaVersion.VERSION_21`, and Gradle's toolchain auto-detection
picked the installed **JDK 17** (Eclipse Adoptium) to run `javac`, which cannot target release 21.

**Recommended fix (apply when build work resumes):** replace the `sourceCompatibility/targetCompatibility`
lines in `build.gradle` with an explicit Java 21 **toolchain** so Gradle uses JDK 21 for compilation:
```gradle
java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}
```
Available JDKs detected on this machine:
- **JDK 21:** `C:\Program Files\Java\jdk-21.0.10` (Oracle) — `JAVA_HOME` points here.
- JDK 17: `C:\Users\752572\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot`.
- (Default `java` on PATH was historically JDK 8.)

**When building, always:** set `JAVA_HOME=C:\Program Files\Java\jdk-21.0.10`, put its `\bin` on PATH,
and run with `--no-daemon` to avoid stale/mismatched daemons.

---

## 6. Environment quirks / gotchas learned

- **Folder was locked** in the original `...\FIT` location (IntelliJ `idea64.exe` + respawning Gradle
  daemons, one parented to `copilot-language-server.exe`). A rename kept failing; the project was
  ultimately **moved** to `...\noteTaker_5000`. Always run gradle with `--no-daemon`; kill only via
  `Stop-Process -Id <PID>`.
- **`api-spec.yaml` was lost once** during process-kill turmoil and had to be recreated — verify file
  state after killing daemons.
- **OpenAPI generator (`spring`) gotchas:** it fails on (1) cross-file parameter `$ref` and (2) shared
  `responses:` objects in schema files. **Fix already applied:** path parameters and error responses are
  **inlined in every operation**. Complex request-body examples are ignored (benign warning).
- **PowerShell tool:** some shells exit with code -1 (no output) when killing daemons.

---

## 7. Technical contracts (must stay consistent across slices)

- **Generated main class:** `com.notetaker.OpenApiGeneratorApplication` (component-scans
  `com.notetaker`, `com.notetaker.api`, `org.openapitools.configuration`). **No hand-written main class.**
- **Delegate interface:** `com.notetaker.api.V1NotesApiDelegate` (tag `V1Notes`).
- **Generated sources:** `build/generated/open-api/src/main/java`; `compileJava dependsOn openApiGenerate`.
- **Models:** Lombok `@Builder` + setters; `SharePermission` enum has `getValue()` / `fromValue()`.
- **Delegate method signatures:**
  - `createNote(CreateNoteRequest) → Note`
  - `getNote(String) → Note`
  - `listNotes(Integer pageNumber, Integer itemsPerPage, String searchTerm) → NotesResponse`
  - `updateNote(String, UpdateNoteRequest) → Note`
  - `completeNote(String) → Note`
  - `deleteNote(String) → Void`
  - `shareNote(String, ShareNoteRequest) → NoteShare`
  - `listNoteShares(String) → NoteSharesResponse`
  - `unshareNote(String, String userId) → Void`
- **Repository methods (must match across slices):**
  - `NoteRepository.findVisibleToUser(userId, pageable)`,
    `findVisibleToUserAndTitle(userId, title, pageable)`, `findByOwnerId(...)`
  - `NoteShareRepository.findByNoteId`, `findBySharedWithUserId`, `deleteByNoteId`
- **Security model (final):** multi-issuer resource server via `JwtIssuerAuthenticationManagerResolver`
  + per-issuer cached `NimbusJwtDecoder` (Nimbus `JWKSourceBuilder.create(url, retriever).cache(...)`
  `.refreshAheadCache(true).rateLimited(false).retrying(true).outageTolerant(...)`). Local/component-test
  `@Order(1)` chain **permits all (no JWT)**. `CurrentUserServiceImpl` reads `employeeNumber` claim from
  the JWT, else falls back to the `X-Employee-Number` header or `notetaker.security.default-user-id`.
- **Data model:** `NOTE` (PK `NOTE_ID`; indexes `NOTE_OWNER_IDX`, `NOTE_OWNER_UPDT_IDX`) and `NOTE_SHARE`
  (composite PK `NOTE_ID` + `SHARED_WITH_USER_ID`; index `NOTE_SHARE_USER_IDX`; FK → `NOTE` cascade).
  Schema `NOTETAKER`. Oracle-compatible types that also work on H2 in Oracle mode. Entities use
  `@CreationTimestamp` / `@UpdateTimestamp` / `@Version`.

### Dependencies (lean set)
Spring Boot 3.5.16, openapi-generator 7.7.0, dependency-management 1.1.6, springdoc 2.8.5,
MapStruct 1.5.5.Final, ojdbc11 21.11.0.0, H2, Liquibase, Caffeine, nimbus-jose-jwt,
mockwebserver 4.12.0 (test).

---

## 8. Resume checklist (do these when unpaused)

1. In `build.gradle`, swap `sourceCompatibility/targetCompatibility` for the Java 21 **toolchain** block (§5).
2. `Stop-Process` any stray Gradle daemons; set `JAVA_HOME` to JDK 21; run
   `./gradlew compileJava --no-daemon`.
3. Reconcile any inter-slice mismatches surfaced by the compiler.
4. Add tests (slice D) and run `./gradlew build --no-daemon` to green.
5. Update `docs/SECURITY.md` for the "local = no JWT" bypass.
6. `git init`, commit (with `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`),
   `git remote add origin https://github.com/1671437_fedex/noteTaker5000.git`. **Do not push without approval.**

---

## 9. Where things live

- **Build:** `build.gradle`, `settings.gradle`, `gradle.properties`
- **API spec:** `openapi/api-spec.yaml`, `openapi/paths/*`, `openapi/schemas/*`
- **Config:** `src/main/resources/application.yml` (Oracle/deployed),
  `src/main/resources/application-local.yml` (H2 + fake issuers), `logback.xml`
- **Domain/impl:** `src/main/java/com/notetaker/notes/v1/**` (entities, repositories, service, mapper,
  controller), `exception/GlobalExceptionHandler.java`
- **Security/config:** `src/main/java/com/notetaker/config/**`, `src/main/java/com/notetaker/security/**`
- **DB changelogs:** `src/main/resources/changelogs/**`
- **Docs:** `README.md`, `docs/{ARCHITECTURE,OPENAPI,DATA-MODEL,SECURITY,PLAN}.md`, this file.
