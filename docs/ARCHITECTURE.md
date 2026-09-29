# Architecture

## Package layout (`com.notetaker`)

```
com.notetaker
├── (OpenApiGeneratorApplication)         # generated @SpringBootApplication main class
├── api / model                           # GENERATED from openapi/ (do not edit by hand)
├── config
│   ├── SecurityConfig                    # multi-issuer resource server, CORS, public URIs, H2 chain
│   ├── SecurityProperties                # binds notetaker.security.*
│   ├── CachedJwtDecoderFactory           # builds cached, refresh-ahead JWKS JwtDecoders (SMS pattern)
│   └── CacheConfig                       # Caffeine CacheManager
├── security
│   ├── CurrentUserService                # extracts employeeNumber from the JWT
│   └── UserIdMissingException
├── notes.v1
│   ├── controller.NoteController         # implements com.notetaker.api.V1NotesApiDelegate
│   ├── service.NoteService (+ Impl)      # business logic + ownership enforcement
│   ├── mapper.NoteMapper                 # MapStruct entity <-> model
│   ├── repository.NoteRepository
│   ├── repository.NoteShareRepository
│   ├── repository.entity.NoteEntity
│   ├── repository.entity.NoteShareEntity (+ NoteShareId)
│   └── exception.{NoteNotFoundException, NoteAccessDeniedException}
└── exception.GlobalExceptionHandler      # @RestControllerAdvice -> ErrorResponse
```

Layering: **generated API interface → controller (delegate impl) → service → repository (JPA)**.
Controllers are thin: map request → call service → map result to generated `model.*` types.

## Shared contracts (agents MUST implement exactly these)

These signatures are fixed so independently-developed slices integrate cleanly.

### Identity
```java
package com.notetaker.security;
public interface CurrentUserService {
  /** Caller's user id from the JWT `employeeNumber` claim (falls back to subject). */
  String getCurrentUserId();
}
```
`UserIdMissingException extends RuntimeException` when neither claim is present → HTTP 401.

### Persistence (JPA / Hibernate)
```java
package com.notetaker.notes.v1.repository.entity;

@Entity @Table(name = "NOTE")               // schema NOTETAKER
class NoteEntity {
  @Id String id;                            // UUID string, assigned in service
  String ownerId;                           // OWNER_ID  (JWT employeeNumber)
  String title;                             // TITLE
  String content;                           // CONTENT (CLOB)
  boolean completed;                        // COMPLETED_FLG
  @CreationTimestamp Instant createdAt;     // REC_CRTN_TMSTP
  @UpdateTimestamp  Instant updatedAt;      // REC_UPDT_TMSTP
  @Version Long version;                    // optimistic locking
}

@Entity @Table(name = "NOTE_SHARE") @IdClass(NoteShareId.class)
class NoteShareEntity {
  @Id String noteId;                        // NOTE_ID
  @Id String sharedWithUserId;              // SHARED_WITH_USER_ID
  String permission;                        // PERMISSION ("READ"/"WRITE")
  @CreationTimestamp Instant createdAt;     // REC_CRTN_TMSTP
}
```
```java
package com.notetaker.notes.v1.repository;
interface NoteRepository extends JpaRepository<NoteEntity, String> {
  Page<NoteEntity> findByOwnerId(String ownerId, Pageable pageable);
  Page<NoteEntity> findByOwnerIdAndTitleContainingIgnoreCase(String ownerId, String title, Pageable pageable);
  // notes visible to a user = owned OR shared-with; see impl (JPQL join to NOTE_SHARE)
}
interface NoteShareRepository extends JpaRepository<NoteShareEntity, NoteShareId> {
  List<NoteShareEntity> findByNoteId(String noteId);
  List<NoteShareEntity> findBySharedWithUserId(String userId);
}
```

### Service
```java
package com.notetaker.notes.v1.service;
public interface NoteService {
  NoteEntity create(String title, String content);
  NoteEntity get(String id);                                   // owner or shared; else 403/404
  Page<NoteEntity> list(int pageNumber, int itemsPerPage, String searchTerm);
  NoteEntity update(String id, String title, String content, Boolean completed); // owner only
  NoteEntity complete(String id);                              // owner only
  void delete(String id);                                      // owner only
  NoteShareEntity share(String id, String sharedWithUserId, String permission);  // owner only
  List<NoteShareEntity> listShares(String id);                 // owner only
  void unshare(String id, String userId);                      // owner only
}
```
Ownership rule: mutating operations compare `note.ownerId` with
`currentUserService.getCurrentUserId()`; mismatch → `NoteAccessDeniedException` (HTTP 403).
Missing note → `NoteNotFoundException` (HTTP 404).

### Generated delegate (implemented by `NoteController`)
`com.notetaker.api.V1NotesApiDelegate`:
- `ResponseEntity<Note> createNote(CreateNoteRequest)`
- `ResponseEntity<Note> getNote(String id)`
- `ResponseEntity<NotesResponse> listNotes(Integer pageNumber, Integer itemsPerPage, String searchTerm)`
- `ResponseEntity<Note> updateNote(String id, UpdateNoteRequest)`
- `ResponseEntity<Note> completeNote(String id)`
- `ResponseEntity<Void> deleteNote(String id)`
- `ResponseEntity<NoteShare> shareNote(String id, ShareNoteRequest)`
- `ResponseEntity<NoteSharesResponse> listNoteShares(String id)`
- `ResponseEntity<Void> unshareNote(String id, String userId)`

Generated `model.*` types use **Lombok builders** (`Note.builder()...build()`), plus setters.
`SharePermission` is an enum (`READ`, `WRITE`).

## Configuration properties (`notetaker.security`)
```
notetaker.security.user-id-claim=employeeNumber
notetaker.security.public-uris=...
notetaker.security.cors-allowed-origins / cors-allowed-methods
notetaker.security.token-issuers.<name>.url / .audience / .keys-uri
notetaker.security.jwks.key-cache-refresh-minutes / .outage-protection-minutes / timeouts
```
