DECLARE
  COUNT_INDEXES INTEGER;
  BEGIN
    SELECT COUNT(*) INTO COUNT_INDEXES
      FROM USER_INDEXES
      WHERE INDEX_NAME = 'IDX_AT';

    IF COUNT_INDEXES > 0 THEN
      EXECUTE IMMEDIATE 'DROP INDEX IDX_AT';
    END IF;
  END;
  /

DECLARE
  COUNT_INDEXES INTEGER;
  BEGIN
    SELECT COUNT(*) INTO COUNT_INDEXES
      FROM USER_INDEXES
      WHERE INDEX_NAME = 'IDX_AUTHORIZATION_CODE';

    IF COUNT_INDEXES > 0 THEN
      EXECUTE IMMEDIATE 'DROP INDEX IDX_AUTHORIZATION_CODE';
    END IF;
  END;
/
ALTER TABLE IDN_OAUTH2_ACCESS_TOKEN modify REFRESH_TOKEN VARCHAR(2048)
/
ALTER TABLE IDN_OAUTH2_ACCESS_TOKEN modify ACCESS_TOKEN VARCHAR(2048)
/
ALTER TABLE IDN_OAUTH2_AUTHORIZATION_CODE modify AUTHORIZATION_CODE VARCHAR(2048)
/
ALTER TABLE IDN_OAUTH_CONSUMER_APPS modify CONSUMER_SECRET VARCHAR(2048)
/
CREATE TABLE IDN_OAUTH2_SCOPE_VALIDATORS (
	APP_ID INTEGER NOT NULL,
	SCOPE_VALIDATOR VARCHAR (128) NOT NULL,
	PRIMARY KEY (APP_ID,SCOPE_VALIDATOR),
	FOREIGN KEY (APP_ID) REFERENCES IDN_OAUTH_CONSUMER_APPS(ID) ON DELETE CASCADE
)
/
CREATE TABLE SP_AUTH_SCRIPT (
  ID         INTEGER      NOT NULL,
  TENANT_ID  INTEGER      NOT NULL,
  APP_ID     INTEGER      NOT NULL,
  TYPE       VARCHAR(255) NOT NULL,
  CONTENT    BLOB    DEFAULT NULL,
  IS_ENABLED CHAR(1) DEFAULT '0',
  PRIMARY KEY (ID)
)
/
CREATE SEQUENCE SP_AUTH_SCRIPT_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE OR REPLACE TRIGGER SP_AUTH_SCRIPT_TRIG
  BEFORE INSERT
  ON SP_AUTH_SCRIPT
  REFERENCING NEW AS NEW
  FOR EACH ROW
  BEGIN
    SELECT SP_AUTH_SCRIPT_SEQ.nextval
    INTO :NEW.ID
    FROM dual;
  END;
/
CREATE TABLE IDN_OIDC_JTI (
  JWT_ID VARCHAR(255) NOT NULL,
  EXP_TIME TIMESTAMP NOT NULL,
  TIME_CREATED TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (JWT_ID))
/

CREATE TABLE IDN_OIDC_PROPERTY (
  ID INTEGER NOT NULL,
  TENANT_ID  INTEGER,
  CONSUMER_KEY  VARCHAR(255) ,
  PROPERTY_KEY  VARCHAR(255) NOT NULL,
  PROPERTY_VALUE  VARCHAR(2047) ,
  PRIMARY KEY (ID),
  FOREIGN KEY (CONSUMER_KEY) REFERENCES IDN_OAUTH_CONSUMER_APPS(CONSUMER_KEY) ON DELETE CASCADE)
/
CREATE SEQUENCE IDN_OIDC_PROPERTY_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE OR REPLACE TRIGGER IDN_OIDC_PROPERTY_TRIG
            BEFORE INSERT
            ON IDN_OIDC_PROPERTY
            REFERENCING NEW AS NEW
            FOR EACH ROW
               BEGIN
                   SELECT IDN_OIDC_PROPERTY_SEQ.nextval INTO :NEW.ID FROM dual;
               END;
/

CREATE TABLE IDN_OIDC_REQ_OBJECT_REFERENCE (
  ID INTEGER,
  CONSUMER_KEY_ID INTEGER ,
  CODE_ID VARCHAR(255) ,
  TOKEN_ID VARCHAR(255) ,
  SESSION_DATA_KEY VARCHAR(255),
  PRIMARY KEY (ID),
  FOREIGN KEY (CONSUMER_KEY_ID) REFERENCES IDN_OAUTH_CONSUMER_APPS(ID) ON DELETE CASCADE,
  FOREIGN KEY (TOKEN_ID) REFERENCES IDN_OAUTH2_ACCESS_TOKEN(TOKEN_ID) ON DELETE CASCADE,
  FOREIGN KEY (CODE_ID) REFERENCES IDN_OAUTH2_AUTHORIZATION_CODE(CODE_ID) ON DELETE CASCADE)
/
CREATE SEQUENCE IDN_OIDC_REQ_OBJECT_REF_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE OR REPLACE TRIGGER IDN_OIDC_REQ_OBJ_REF_TRIG
            BEFORE INSERT
            ON IDN_OIDC_REQ_OBJECT_REFERENCE
            REFERENCING NEW AS NEW
            FOR EACH ROW
               BEGIN
                   SELECT IDN_OIDC_REQ_OBJECT_REF_SEQ.nextval INTO :NEW.ID FROM dual;
               END;
/

CREATE TABLE IDN_OIDC_REQ_OBJECT_CLAIMS (
  ID INTEGER,
  REQ_OBJECT_ID INTEGER ,
  CLAIM_ATTRIBUTE VARCHAR(255) ,
  ESSENTIAL CHAR(1) DEFAULT '0',
  VALUE VARCHAR(255),
  IS_USERINFO CHAR(1) DEFAULT '0',
  PRIMARY KEY (ID),
  FOREIGN KEY (REQ_OBJECT_ID) REFERENCES IDN_OIDC_REQ_OBJECT_REFERENCE(ID) ON DELETE CASCADE)
/
CREATE SEQUENCE IDN_OIDC_REQ_OBJ_CLAIMS_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE OR REPLACE TRIGGER IDN_OIDC_REQ_OBJ_CLAIMS_TRIG
            BEFORE INSERT
            ON IDN_OIDC_REQ_OBJECT_CLAIMS
            REFERENCING NEW AS NEW
            FOR EACH ROW
               BEGIN
                   SELECT IDN_OIDC_REQ_OBJ_CLAIMS_SEQ.nextval INTO :NEW.ID FROM dual;
               END;
/

CREATE TABLE IDN_OIDC_REQ_OBJ_CLAIM_VALUES (
  ID INTEGER,
  REQ_OBJECT_CLAIMS_ID INTEGER,
  CLAIM_VALUES VARCHAR(255),
  PRIMARY KEY (ID),
  FOREIGN KEY (REQ_OBJECT_CLAIMS_ID) REFERENCES IDN_OIDC_REQ_OBJECT_CLAIMS(ID) ON DELETE CASCADE)
/
CREATE SEQUENCE IDN_OIDC_REQ_OBJ_CLM_VAL_SEQ START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE OR REPLACE TRIGGER IDN_OIDC_REQ_OBJ_CLM_VAL_TRIG
            BEFORE INSERT
            ON IDN_OIDC_REQ_OBJ_CLAIM_VALUES
            REFERENCING NEW AS NEW
            FOR EACH ROW
               BEGIN
                   SELECT IDN_OIDC_REQ_OBJ_CLM_VAL_SEQ.nextval INTO :NEW.ID FROM dual;
               END;
/

CREATE TABLE IDN_CERTIFICATE (
            ID INTEGER,
            NAME VARCHAR(100),
            CERTIFICATE_IN_PEM BLOB,
            TENANT_ID INTEGER DEFAULT 0,
            PRIMARY KEY(ID),
            CONSTRAINT CERTIFICATE_UNIQUE_KEY UNIQUE (NAME, TENANT_ID))
/
CREATE SEQUENCE IDN_CERTIFICATE_SEQUENCE START WITH 1 INCREMENT BY 1 NOCACHE
/
CREATE OR REPLACE TRIGGER IDN_CERTIFICATE_TRIGGER
		    BEFORE INSERT
            ON IDN_CERTIFICATE
            REFERENCING NEW AS NEW
            FOR EACH ROW
            BEGIN
                SELECT IDN_CERTIFICATE_SEQUENCE.nextval INTO :NEW.ID FROM dual;
            END;
/