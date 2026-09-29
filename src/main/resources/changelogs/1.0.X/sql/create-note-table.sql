CREATE TABLE NOTETAKER.NOTE
(
    NOTE_ID          NUMBER        GENERATED ALWAYS AS IDENTITY,
    WORKSPACE_ID     NUMBER        NOT NULL,           -- owning collaborative workspace
    TITLE_TXT        VARCHAR2(300) NOT NULL,
    CONTENT_TXT      CLOB,
    COMPLETED_FLG    CHAR(1)       DEFAULT 'F' NOT NULL,  -- 'T' when the note is marked complete
    VERS_NBR         NUMBER(19)    DEFAULT 0   NOT NULL,  -- optimistic-lock + version counter (JPA @Version)
    CRTD_BY_USER_ID  NUMBER        NOT NULL,           -- app_user who created the note
    REC_CRTN_TMSTP   TIMESTAMP(6)  NOT NULL,
    REC_MODIFY_TMSTP TIMESTAMP(6)  NOT NULL,
    DEL_TMSTP        TIMESTAMP(6),                     -- set when moved to trash; null while active

    CONSTRAINT NOTE_PK PRIMARY KEY (NOTE_ID),
    CONSTRAINT NOTE_COMPLETED_FLG_CK CHECK (COMPLETED_FLG IN ('T', 'F')),
    CONSTRAINT NOTE_WORKSPACE_FK FOREIGN KEY (WORKSPACE_ID)
        REFERENCES NOTETAKER.WORKSPACE (WORKSPACE_ID) ON DELETE CASCADE,
    CONSTRAINT NOTE_CRTD_BY_FK FOREIGN KEY (CRTD_BY_USER_ID)
        REFERENCES NOTETAKER.APP_USER (USER_ID)
);

-- Powers "list notes in a workspace" for collaborative listings without a full scan.
CREATE INDEX NOTETAKER.NOTE_IX1 ON NOTETAKER.NOTE (WORKSPACE_ID);
-- Powers "notes I created" lookups.
CREATE INDEX NOTETAKER.NOTE_IX2 ON NOTETAKER.NOTE (CRTD_BY_USER_ID);
