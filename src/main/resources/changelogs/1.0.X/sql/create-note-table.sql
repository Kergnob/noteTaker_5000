CREATE TABLE NOTETAKER.NOTE
(
    NOTE_ID         VARCHAR2(36)  NOT NULL,
    OWNER_ID        VARCHAR2(50)  NOT NULL,
    TITLE           VARCHAR2(200) NOT NULL,
    CONTENT         CLOB,
    COMPLETED_FLG   NUMBER(1) DEFAULT 0 NOT NULL,
    REC_CRTN_TMSTP  TIMESTAMP     NOT NULL,
    REC_UPDT_TMSTP  TIMESTAMP     NOT NULL,
    VERSION         NUMBER(19)    NOT NULL,

    CONSTRAINT NOTE_PK PRIMARY KEY (NOTE_ID)
);

-- Powers "list my notes" lookups by owner without scanning all notes.
CREATE INDEX NOTETAKER.NOTE_OWNER_IDX ON NOTETAKER.NOTE (OWNER_ID);

-- Powers owner listing ordered by most-recent update for efficient paging without a full scan/sort.
CREATE INDEX NOTETAKER.NOTE_OWNER_UPDT_IDX ON NOTETAKER.NOTE (OWNER_ID, REC_UPDT_TMSTP);
