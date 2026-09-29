CREATE TABLE NOTETAKER.APP_USER
(
    USER_ID          NUMBER        GENERATED ALWAYS AS IDENTITY,
    EMP_NBR          VARCHAR2(50)  NOT NULL,  -- external identity (JWT employeeNumber / Purple ID)
    EMAIL_TXT        VARCHAR2(255) NOT NULL,  -- contact email (synthesized on auto-provision)
    DSPL_NM          VARCHAR2(100) NOT NULL,  -- display name
    REC_CRTN_TMSTP   TIMESTAMP(6)  NOT NULL,  -- record creation timestamp
    REC_MODIFY_TMSTP TIMESTAMP(6)  NOT NULL,  -- record modification timestamp

    CONSTRAINT APP_USER_PK PRIMARY KEY (USER_ID),
    CONSTRAINT APP_USER_EMP_NBR_UK UNIQUE (EMP_NBR),
    CONSTRAINT APP_USER_EMAIL_UK UNIQUE (EMAIL_TXT)
);
