# Build-out plan & status

Initial scaffold is split into independent slices, each owned by a coding agent. Contracts are
fixed in [`ARCHITECTURE.md`](ARCHITECTURE.md) so slices integrate cleanly.

| # | Slice | Files (packages) | Status |
|---|-------|------------------|--------|
| 0 | Build & spec | `build.gradle`, `settings.gradle`, `openapi/**` | ✅ done |
| 0 | Docs | `README.md`, `docs/**` | ✅ done |
| A | Persistence + Liquibase + resources | `notes.v1.repository.**`, `resources/application*.yml`, `resources/changelogs/**`, `logback.xml` | ✅ done |
| B | Security + config | `config.SecurityConfig`, `config.CachedJwtDecoderFactory`, `config.CacheConfig`, `security.**` | ✅ done |
| C | Service + controller + mapper + errors | `notes.v1.service.**`, `notes.v1.controller.**`, `notes.v1.mapper.**`, `notes.v1.exception.**`, `exception.GlobalExceptionHandler` | ✅ done |
| D | Tests (Spock unit + component) | `src/test/groovy/**`, `src/test/resources/application-component-test.yml` | ✅ done |
| E | Integration build + git init | `./gradlew build`, `git init` + remote | ⏳ (build ✅ green; git init pending) |

**What's next / future:** see [`ROADMAP.md`](ROADMAP.md) for the current-vs-future feature
breakdown and the real-time-collaboration decision.

## Definition of done
- `./gradlew build` green on JDK 21 (API generated, compiles, tests pass, coverage report).
- Create/read/list/update/complete/delete + share/unshare working on H2 (`local` profile).
- Owner-only enforcement verified via the JWT `employeeNumber`.
- 401 without a token; 200 with a bypass-issuer token; JWKS cache exercised.
- Oracle-ready: entities/changelogs use Oracle-compatible types; indexes declared.

## Deferred (post-v1)
Rich content/attachments, team/group sharing, full-text search, soft delete + audit history,
outbound integrations, a validated-token (not just JWKS) cache.
