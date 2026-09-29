# NoteTaker5000 — Roadmap: current state vs. future work

> **Purpose:** a timeboxed snapshot of what the service does **today** versus what a fuller
> product would add. Items that are genuinely more technically demanding are deferred here
> rather than half-built. This doc is the backlog; `PLAN.md` tracks the current slice status.
>
> **Last updated:** 2026-09-28

---

## 0. The pivotal decision: real-time collaboration

The single biggest architectural driver is **whether two people edit the same note at the
same time**. It changes the content format, the storage model, the sync layer, and the API.

- **v1 choice (implemented):** **single-writer with optimistic concurrency.** One person edits
  at a time; others read (and, once shares land richer, comment). Concurrent saves are made
  safe by a JPA `@Version` column (optimistic lock → HTTP 409 on conflict) instead of silent
  last-write-wins.
- **Why:** it satisfies "shared amongst small teams" for capture-and-share workflows, is cheap
  to build, and does **not** paint us into a corner. The note schema keeps `content` as a
  single text/CLOB field, so a block/CRDT model can be layered on later behind the same API.
- **Revisit when:** users actually need simultaneous co-editing. That triggers the Phase 4
  work below (CRDT/OT, WebSocket/SSE, presence) — a distinct storage and sync model, correctly
  treated as future work.

**Open question to confirm with stakeholders:** is it ever "two people typing in one note at
once," or always "one editor, others read/comment"? If the latter, the current design is the
right long-term base and only needs incremental features, not a rewrite.

---

## 1. Current state (implemented and tested)

| Area | Status | Notes |
|------|--------|-------|
| **Notes CRUD** | ✅ | Create, read, list, update, complete, delete via `/api/v1/notes`. |
| **Ownership model** | ✅ | Every note owned by the JWT `employeeNumber`; mutations are owner-only (403 otherwise). |
| **Sharing / ACL (basic)** | ✅ | Share/unshare a note with another user at `READ`/`WRITE`; sharee gets read access; owner-only management. |
| **"Shared with me" visibility** | ✅ | `listNotes` returns owned **and** shared-with-me notes; each note flagged `shared`. |
| **Title search** | ✅ | Case-insensitive `searchTerm` filter over the note title. |
| **Pagination** | ✅ | Offset paging (`pageNumber`/`itemsPerPage`) with `pageInfo` (total items, page count). |
| **Optimistic concurrency** | ✅ | `@Version` on `NOTE`; concurrent saves surface as HTTP 409, not silent overwrite. |
| **Persistence** | ✅ | JPA/Hibernate on H2 (Oracle-compat) locally, Oracle for deployed; explicit PKs/indexes in Liquibase. |
| **Multi-issuer JWT auth** | ✅ | OAuth2 resource server, `JwtIssuerAuthenticationManagerResolver`, per-issuer cached refresh-ahead JWKS (SMS pattern). |
| **Local "bypass" profile** | ✅ | `local`/`component-test` require no JWT; `X-Employee-Number` header impersonates a user for demos/tests. |
| **Spec-first API** | ✅ | OpenAPI in `openapi/`; Spring delegates generated; Swagger UI. |
| **Error contract** | ✅ | `@RestControllerAdvice` → structured `ErrorResponse` (400/401/403/404/409/500). |
| **Tests** | ✅ | Spock unit specs (service, controller, mapper, security, error handler) + a component-test package that boots the app, drives the HTTP API, and preps data via the H2 repositories. |

### How the current build maps to the reference domain model
| Reference concept | Today |
|-------------------|-------|
| User / identity | JWT `employeeNumber` as `ownerId` (no local user table). |
| Note + metadata | `NOTE` (owner, title, content, completed, created/updated, version). |
| Share / ACL | `NOTE_SHARE` (note + user + READ/WRITE). |
| Optimistic concurrency | `@Version` column. |
| Everything else below | **Not yet** — see §2. |

---

## 2. Future work (deferred — organized by the reference phasing)

Grouped roughly easiest → hardest. Each item notes *why it's deferred*.

### Phase 1 — round out the core domain (moderate effort)
- **Workspace/Team tenant boundary** with `tenant_id` on every table + Postgres/Oracle
  row-level security. *Deferred:* current model is per-user, not multi-tenant; retrofitting
  tenancy touches every table, so it's a deliberate scoping call, not a quick add.
- **Notebook/Folder hierarchy** (`parent_id` tree). *Deferred:* needs recursive queries and
  move/reparent semantics.
- **Tags** (many-to-many) and tag-scoped listing.
- **Soft delete + trash/restore window** and a data-retention policy. *Deferred:* changes the
  delete semantics and every visibility query; easier to design in deliberately than bolt on.
- **Role-based permissions** at workspace *and* note level (owner/editor/viewer), plus
  link-sharing. *Deferred:* today's ACL is per-note READ/WRITE only; a central policy layer is
  a larger design.
- **Cursor pagination + idempotency keys on writes.** *Deferred:* offset paging is adequate at
  current scale; idempotency needs a stored request-key table.

### Phase 2 — search, history, files, audit (moderate → high effort)
- **Full-text search** (start with DB FTS; move to OpenSearch via an outbox pattern only when
  relevance/scale demands). *Deferred:* current search is a title `LIKE`.
- **Version history** (`NoteVersion`, immutable) with restore, via periodic snapshots + deltas
  (not a copy per keystroke). *Deferred:* new storage model + retention tuning.
- **Attachments** in object storage (S3-style) with pre-signed URLs and DB metadata.
  *Deferred:* requires object storage + a worker for thumbnails/virus scan.
- **Audit log** (`AuditEvent`) of security-relevant actions.

### Phase 3 — collaboration primitives (high effort)
- **Comments / threads**, **@mentions**, and **notifications** (needs an async worker + queue).
- **Backlinks / note-to-note linking.**
- **Templates**, **export/import** (Markdown, PDF), **webhooks**.

### Phase 4 — real-time & offline (highest effort — the §0 decision)
- **Real-time co-editing + presence** via CRDT (Yjs/Automerge) or OT, over WebSocket/SSE, with
  Redis pub/sub to fan out across instances. *Deferred:* a fundamentally different content and
  sync model; gated on the §0 decision.
- **Offline sync** for clients.
- **Semantic search / AI summaries.**

---

## 3. Cross-cutting concerns — current vs. future
| Concern | Today | Future |
|---------|-------|--------|
| API contract | ✅ OpenAPI-first, Swagger UI | GraphQL only if clients get complex |
| AuthN | ✅ OIDC/JWT, multi-issuer, cached JWKS | SSO (SAML), short-lived token rotation |
| AuthZ | ✅ owner-only + per-note ACL in service | central policy layer; RLS for tenancy |
| Persistence | ✅ Oracle/H2, explicit indexes | Postgres option (JSONB blocks, FTS, RLS) |
| Concurrency | ✅ optimistic `@Version` | CRDT/OT for simultaneous editing |
| Observability | logback + Actuator health/info | structured logs w/ correlation IDs, latency/queue metrics |
| Resilience/ops | H2 local, pipeline Liquibase | backups w/ tested restores, per-tenant rate limiting, encryption at rest |
| Async | none (synchronous) | queue + workers: indexing, notifications, thumbnails, exports, retention |

---

## 4. Architecture direction (when we grow beyond v1)
Keep a **modular monolith** (identity, notes, collaboration, search, files) with clean internal
boundaries so modules can split into services later. Add a WebSocket/SSE channel and Redis only
when real-time lands. Prefer an **outbox pattern** for any external search index to keep it
consistent. Put `tenant_id` on every table the day multi-tenancy is adopted — it's the one
thing that's genuinely painful to retrofit.
