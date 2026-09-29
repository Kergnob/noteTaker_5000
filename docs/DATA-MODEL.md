# Data model

Schema: **`NOTETAKER`** (created automatically on H2; provisioned by the pipeline on Oracle).
All timestamps are UTC `Instant`s managed by Hibernate (`@CreationTimestamp` / `@UpdateTimestamp`).

## Tables

### `NOTE`
| Column          | Type            | Notes                                   |
|-----------------|-----------------|-----------------------------------------|
| `NOTE_ID`       | VARCHAR2(36)    | **PK** — UUID string                    |
| `OWNER_ID`      | VARCHAR2(50)    | JWT `employeeNumber` of the owner       |
| `TITLE`         | VARCHAR2(200)   | not null                                |
| `CONTENT`       | CLOB            | nullable                                |
| `COMPLETED_FLG` | NUMBER(1)       | 0/1, default 0                          |
| `REC_CRTN_TMSTP`| TIMESTAMP       | creation timestamp                      |
| `REC_UPDT_TMSTP`| TIMESTAMP       | last-update timestamp                   |
| `VERSION`       | NUMBER(19)      | optimistic-lock version                 |

**Indexes / keys (performance):**
- `NOTE_PK` PRIMARY KEY (`NOTE_ID`).
- `NOTE_OWNER_IDX` on (`OWNER_ID`) — powers *list my notes*.
- `NOTE_OWNER_UPDT_IDX` on (`OWNER_ID`, `REC_UPDT_TMSTP`) — powers owner listing ordered by
  most-recently-updated (paging without a full scan/sort).

### `NOTE_SHARE`
| Column                | Type          | Notes                                  |
|-----------------------|---------------|----------------------------------------|
| `NOTE_ID`             | VARCHAR2(36)  | **PK part**, FK → `NOTE(NOTE_ID)`      |
| `SHARED_WITH_USER_ID` | VARCHAR2(50)  | **PK part** — recipient user id        |
| `PERMISSION`          | VARCHAR2(10)  | `READ` / `WRITE`                       |
| `REC_CRTN_TMSTP`      | TIMESTAMP     | creation timestamp                     |

**Indexes / keys (performance):**
- `NOTE_SHARE_PK` PRIMARY KEY (`NOTE_ID`, `SHARED_WITH_USER_ID`) — natural composite key,
  prevents duplicate shares and serves owner *list shares of a note*.
- `NOTE_SHARE_USER_IDX` on (`SHARED_WITH_USER_ID`) — powers *notes shared with me*.
- `NOTE_SHARE_NOTE_FK` FOREIGN KEY (`NOTE_ID`) → `NOTE(NOTE_ID)` (cascade delete of shares).

## Liquibase layout
```
src/main/resources/changelogs/
├── db.root-master.yaml           # includes each version master
└── 1.0.X/
    ├── db.version-master.yaml    # changesets for this version
    └── sql/
        ├── create-note-table.sql
        └── create-note-share-table.sql
```
Each `sqlFile` changeset creates a table **and its indexes/constraints in the same script**,
with comments explaining why each index exists (Oracle stays index-driven, not full-scan).
Local/`component-test` profiles run Liquibase automatically; higher environments run it via
the deployment pipeline.
