# OpenAPI: how the API is built & extended

NoteTaker is **spec-first**, following the FIT worklist service pattern. The contract in
[`openapi/`](../openapi) is the source of truth; Java interfaces and models are **generated**
at build time and must never be edited by hand.

## Toolchain

- Plugin: `org.openapi.generator` (generator `spring`).
- Key options (see `build.gradle` `openApiGenerate {}`):
  - `delegatePattern = true` → generates `XxxApi` (the Spring `@RestController`) **and** a
    `XxxApiDelegate` interface. We implement the **delegate**, keeping generated controllers untouched.
  - `useTags = true` → interfaces are named from the operation `tags` (tag `V1Notes` →
    `V1NotesApi` / `V1NotesApiDelegate`).
  - `useSpringBoot3 = true`, `useBeanValidation = true`, `performBeanValidation = true`.
  - `dateLibrary = custom` with `typeMappings` `DateTime→Instant`, `date→LocalDate`.
  - `additionalModelTypeAnnotations` add Lombok `@Builder/@NoArgsConstructor/@AllArgsConstructor`
    and `@JsonInclude(NON_NULL)` to every model.
- Generated output: `build/generated/open-api/src/main/java`, wired into the main source set.
  `compileJava dependsOn openApiGenerate`.
- The generated `OpenApiGeneratorApplication` is the Spring Boot **main class** (component-scans
  `com.notetaker`, `com.notetaker.api`, `org.openapitools.configuration`).

## Spec structure

```
openapi/
├── api-spec.yaml                 # root: info, servers, tags, paths (as $refs), securitySchemes
├── paths/                        # one file per URL, one operation per HTTP method
│   ├── notes.yaml                # GET list, POST create
│   ├── note-by-id.yaml           # GET, PUT, DELETE
│   ├── note-complete.yaml        # POST complete
│   ├── note-shares.yaml          # GET, POST
│   └── note-share-by-user.yaml   # DELETE
└── schemas/                      # reusable models (with examples)
    ├── note.yaml                 # Note, CreateNoteRequest, UpdateNoteRequest
    ├── note-share.yaml           # NoteShare, ShareNoteRequest, NoteSharesResponse, SharePermission
    ├── notes-response.yaml       # NotesResponse (page)
    └── common/                   # error-response.yaml, page-info.yaml
```

### Conventions learned from the worklist reference
- **Path parameters are declared inline** in each operation (do **not** `$ref` a parameter
  defined in another file — the generator resolves cross-file parameter refs relative to the
  root doc and fails to load them).
- **Error responses are written inline** per operation, each referencing
  `../schemas/common/error-response.yaml#/ErrorResponse` (avoid shared `responses:` objects
  in a schema file).
- Every operation carries `security: [ { BearerAuth: [] } ]` and a `tags: [ V1Notes ]`.
- Schemas carry `example:` values so Swagger UI and generated docs show realistic payloads.

## Extending the API — add a new endpoint

1. **Add/att a schema** in `openapi/schemas/…` (with `example`s).
2. **Add a path file** under `openapi/paths/…`; declare the operation with `operationId`,
   `tags: [ V1Notes ]`, inline path params, `security`, request/response bodies, and inline
   error responses.
3. **Register the path** in `openapi/api-spec.yaml` under `paths:` via `$ref`.
4. **Regenerate**: `./gradlew openApiGenerate` (or just `build`). A new method appears on
   `V1NotesApiDelegate` (or a new delegate if you introduce a new tag).
5. **Implement** the new delegate method in `NoteController` (delegate the work to
   `NoteService`); add service/repository logic as needed.
6. Add a Liquibase changeset if the data model changes (see `docs/DATA-MODEL.md`).

To add a **new resource** (its own delegate), give its operations a new tag (e.g. `V1Folders`)
and implement a new `@Component` controller implementing the generated `V1FoldersApiDelegate`.

## Validate locally
```bash
./gradlew openApiGenerate           # fails fast on an invalid spec
./gradlew bootRun --args='--spring.profiles.active=local'
# Swagger UI: http://localhost:8080/swagger-ui.html
```
