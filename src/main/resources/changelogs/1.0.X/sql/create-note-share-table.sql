CREATE TABLE NOTETAKER.NOTE_SHARE
(
    NOTE_ID              VARCHAR2(36) NOT NULL,
    SHARED_WITH_USER_ID  VARCHAR2(50) NOT NULL,
    PERMISSION           VARCHAR2(10) NOT NULL,
    REC_CRTN_TMSTP       TIMESTAMP    NOT NULL,

    -- Natural composite key prevents duplicate shares and serves owner list-shares lookups.
    CONSTRAINT NOTE_SHARE_PK PRIMARY KEY (NOTE_ID, SHARED_WITH_USER_ID),

    -- Cascades share cleanup when a note is deleted.
    CONSTRAINT NOTE_SHARE_NOTE_FK FOREIGN KEY (NOTE_ID)
        REFERENCES NOTETAKER.NOTE (NOTE_ID) ON DELETE CASCADE
);

-- Powers "notes shared with me" lookups by recipient without scanning all shares.
CREATE INDEX NOTETAKER.NOTE_SHARE_USER_IDX ON NOTETAKER.NOTE_SHARE (SHARED_WITH_USER_ID);
