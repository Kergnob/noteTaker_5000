# NoteTaker5000 — Roadmap

What the service does today, and the features a fuller product would add next. Genuinely
harder work is deferred here rather than half-built. The README covers today's features in
detail; this doc is the forward-looking backlog.

---

## Where we are today

Delivered and tested: workspaces with role-based access (OWNER/EDITOR/VIEWER), notes CRUD +
complete, title search, offset pagination, soft-delete with trash/restore, optimistic
concurrency (HTTP 409 on stale saves), auto-provisioned users from the JWT `employeeNumber`,
multi-issuer JWT auth with cached JWKS, and a spec-first OpenAPI contract. Persistence is
JPA/Liquibase on H2 (Oracle mode) locally and Oracle when deployed.

Everything below is **not** built yet.

---

## The one decision that shapes everything: real-time co-editing

The biggest architectural fork is **whether two people edit the same note simultaneously**. It
changes the content format, storage, sync layer, and API.

- **Today:** single-writer with optimistic concurrency — one person edits at a time, others
  read; concurrent saves are rejected with HTTP 409 instead of silently overwriting. `content`
  is a single text field, so a block/CRDT model can be layered behind the same API later.
- **Revisit when** users actually need simultaneous co-editing (the Phase 4 work below).

**To confirm with stakeholders:** is it ever "two people typing in one note at once," or
always "one editor, others read/comment"? If the latter, today's design is the right long-term
base and only needs incremental features — not a rewrite.

---

## Future features (roughly easiest → hardest)

### Phase 2 — round out the core domain
- **Folders / notebooks** (`parent_id` tree) — needs recursive queries and move/reparent rules.
- **Tags** (many-to-many) and tag-scoped listing.
- **Note version history** — immutable snapshots + restore, via periodic snapshots/deltas (not
  a copy per keystroke).
- **Retention/purge policy** for trashed notes (timed hard-delete).
- **Cursor pagination + idempotency keys on writes** — offset paging is fine at current scale;
  idempotency needs a stored request-key table.

### Phase 3 — search, files, audit
- **Full-text search** — start with database FTS; move to a search engine (via an outbox
  pattern) only when relevance/scale demands it. Today's search is a title `LIKE`.
- **Attachments** in object storage with pre-signed URLs and DB metadata; a worker for
  thumbnails/scanning.
- **Audit log** of security-relevant actions.
- **Row-level security / DB-enforced tenancy** — access is enforced in the service layer today;
  pushing it into the database is a larger, separate change.

### Phase 4 — collaboration primitives
- **Comments / threads**, **@mentions**, **notifications** (needs an async worker + queue).
- **Backlinks / note-to-note linking**, **templates**, **export/import** (Markdown, PDF),
  **webhooks**.
- **Per-note roles and link-sharing** on top of today's workspace roles.

### Phase 5 — real-time & offline (the decision above)
- **Real-time co-editing + presence** via CRDT (Yjs/Automerge) or OT, over WebSocket/SSE, with
  a pub/sub layer to fan out across instances — a fundamentally different content/sync model.
- **Offline sync** for clients.
- **Semantic search / AI summaries.**

---

## Cross-cutting direction (as it grows)

- **Stay a modular monolith** (identity, notes, collaboration, search, files) with clean
  internal boundaries so modules can split into services later.
- **AuthZ:** add per-note roles and a central policy layer; consider RLS for tenancy.
- **Async:** introduce a queue + workers only when indexing, notifications, thumbnails,
  exports, or retention arrive.
- **Observability:** structured logs with correlation IDs and latency/queue metrics.
- **Ops:** tested backup/restore, per-workspace rate limiting, encryption at rest.
- Prefer an **outbox pattern** for any external index to keep it consistent.
