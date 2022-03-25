ALTER TABLE AM_API ADD API_UUID VARCHAR(255);
ALTER TABLE AM_API ADD STATUS VARCHAR(30);
ALTER TABLE AM_API ADD REVISIONS_CREATED INTEGER DEFAULT 0;
ALTER TABLE AM_CERTIFICATE_METADATA ADD CERTIFICATE BYTEA DEFAULT NULL;

DROP TABLE IF EXISTS AM_REVISION;
CREATE TABLE IF NOT EXISTS AM_REVISION (
            ID INTEGER NOT NULL,
            API_UUID VARCHAR(256) NOT NULL,
            REVISION_UUID VARCHAR(255) NOT NULL,
            DESCRIPTION VARCHAR(255),
            CREATED_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            CREATED_BY VARCHAR(255),
            PRIMARY KEY (ID, API_UUID),
            UNIQUE(REVISION_UUID)
);

DROP TABLE IF EXISTS AM_API_REVISION_METADATA;
CREATE TABLE IF NOT EXISTS AM_API_REVISION_METADATA (
    API_UUID VARCHAR(64),
    REVISION_UUID VARCHAR(64),
    API_TIER VARCHAR(128),
    UNIQUE (API_UUID,REVISION_UUID)
);

DROP TABLE IF EXISTS AM_DEPLOYMENT_REVISION_MAPPING;
CREATE TABLE IF NOT EXISTS AM_DEPLOYMENT_REVISION_MAPPING (
            NAME VARCHAR(255) NOT NULL,
            VHOST VARCHAR(255) NULL,
            REVISION_UUID VARCHAR(255) NOT NULL,
            DISPLAY_ON_DEVPORTAL BOOLEAN DEFAULT '0',
            DEPLOYED_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (NAME, REVISION_UUID),
            FOREIGN KEY (REVISION_UUID) REFERENCES AM_REVISION(REVISION_UUID) ON UPDATE CASCADE ON DELETE CASCADE
);

ALTER TABLE AM_API_CLIENT_CERTIFICATE ADD REVISION_UUID VARCHAR(255) NOT NULL DEFAULT 'Current API';
ALTER TABLE AM_API_CLIENT_CERTIFICATE DROP CONSTRAINT AM_API_CLIENT_CERTIFICATE_PKEY;
ALTER TABLE AM_API_CLIENT_CERTIFICATE ADD PRIMARY KEY(ALIAS,TENANT_ID, REMOVED, REVISION_UUID);

ALTER TABLE AM_API_URL_MAPPING ADD REVISION_UUID VARCHAR(256);

ALTER TABLE AM_GRAPHQL_COMPLEXITY ADD REVISION_UUID VARCHAR(256);

ALTER TABLE AM_API_PRODUCT_MAPPING ADD REVISION_UUID VARCHAR(256);

DROP TABLE IF EXISTS AM_GW_API_DEPLOYMENTS;
DROP TABLE IF EXISTS AM_GW_API_ARTIFACTS;
DROP TABLE IF EXISTS AM_GW_PUBLISHED_API_DETAILS;

CREATE TABLE IF NOT EXISTS AM_GW_PUBLISHED_API_DETAILS (
  API_ID varchar(255) NOT NULL,
  TENANT_DOMAIN varchar(255),
  API_PROVIDER varchar(255),
  API_NAME varchar(255),
  API_VERSION varchar(255),
  API_TYPE varchar(50),
  PRIMARY KEY (API_ID)
);

CREATE TABLE IF NOT EXISTS AM_GW_API_ARTIFACTS (
  API_ID VARCHAR(255) NOT NULL,
  REVISION_ID VARCHAR(255) NOT NULL,
  ARTIFACT bytea,
  TIME_STAMP TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (REVISION_ID, API_ID),
  FOREIGN KEY (API_ID) REFERENCES AM_GW_PUBLISHED_API_DETAILS(API_ID) ON UPDATE CASCADE ON DELETE NO ACTION
);
CREATE TABLE IF NOT EXISTS AM_GW_API_DEPLOYMENTS (
  API_ID VARCHAR(255) NOT NULL,
  REVISION_ID VARCHAR(255) NOT NULL,
  LABEL VARCHAR(255) NOT NULL,
  VHOST VARCHAR(255) NULL,
  PRIMARY KEY (REVISION_ID, API_ID,LABEL),
  FOREIGN KEY (API_ID) REFERENCES AM_GW_PUBLISHED_API_DETAILS(API_ID) ON UPDATE CASCADE ON DELETE NO ACTION
);

CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.TIME_STAMP= now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER TIME_STAMP AFTER UPDATE ON AM_GW_API_ARTIFACTS FOR EACH ROW EXECUTE PROCEDURE  update_modified_column();

-- Service Catalog --
DROP TABLE IF EXISTS AM_SERVICE_CATALOG;
CREATE TABLE IF NOT EXISTS AM_SERVICE_CATALOG (
            UUID VARCHAR(36) NOT NULL,
            SERVICE_KEY VARCHAR(100) NOT NULL,
            MD5 VARCHAR(100) NOT NULL,
            SERVICE_NAME VARCHAR(255) NOT NULL,
            DISPLAY_NAME VARCHAR(255) NOT NULL,
            SERVICE_VERSION VARCHAR(30) NOT NULL,
            TENANT_ID INTEGER NOT NULL,
            SERVICE_URL VARCHAR(2048) NOT NULL,
            DEFINITION_TYPE VARCHAR(20),
            DEFINITION_URL VARCHAR(2048),
            DESCRIPTION VARCHAR(1024),
            SECURITY_TYPE VARCHAR(50),
            MUTUAL_SSL_ENABLED BOOLEAN DEFAULT '0',
            CREATED_TIME TIMESTAMP NULL,
            LAST_UPDATED_TIME TIMESTAMP NULL,
            CREATED_BY VARCHAR(255),
            UPDATED_BY VARCHAR(255),
            SERVICE_DEFINITION BYTEA NOT NULL,
            METADATA BYTEA NOT NULL,
            PRIMARY KEY (UUID),
            UNIQUE (SERVICE_NAME, SERVICE_VERSION, TENANT_ID),
            UNIQUE (SERVICE_KEY, TENANT_ID)
);

-- Webhooks --
DROP SEQUENCE IF EXISTS AM_WEBHOOKS_PK_SEQ;
CREATE SEQUENCE AM_WEBHOOKS_PK_SEQ;
DROP TABLE IF EXISTS AM_WEBHOOKS_SUBSCRIPTION;
CREATE TABLE IF NOT EXISTS AM_WEBHOOKS_SUBSCRIPTION (
            WH_SUBSCRIPTION_ID INTEGER DEFAULT NEXTVAL('AM_WEBHOOKS_PK_SEQ'),
            API_UUID VARCHAR(255) NOT NULL,
            APPLICATION_ID VARCHAR(20) NOT NULL,
            TENANT_DOMAIN VARCHAR(255) NOT NULL,
            HUB_CALLBACK_URL VARCHAR(1024) NOT NULL,
            HUB_TOPIC VARCHAR(255) NOT NULL,
            HUB_SECRET VARCHAR(2048),
            HUB_LEASE_SECONDS INTEGER,
            UPDATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            EXPIRY_AT BIGINT,
            DELIVERED_AT TIMESTAMP NULL,
            DELIVERY_STATE INTEGER,
            PRIMARY KEY (WH_SUBSCRIPTION_ID)
);

DROP TABLE IF EXISTS AM_WEBHOOKS_UNSUBSCRIPTION;
CREATE TABLE IF NOT EXISTS AM_WEBHOOKS_UNSUBSCRIPTION (
          API_UUID VARCHAR(255) NOT NULL,
          APPLICATION_ID VARCHAR(20) NOT NULL,
          TENANT_DOMAIN VARCHAR(255) NOT NULL,
          HUB_CALLBACK_URL VARCHAR(1024) NOT NULL,
          HUB_TOPIC VARCHAR(255) NOT NULL,
          HUB_SECRET VARCHAR(2048),
          HUB_LEASE_SECONDS INTEGER,
          ADDED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS AM_API_SERVICE_MAPPING;
CREATE TABLE AM_API_SERVICE_MAPPING (
    API_ID INTEGER NOT NULL,
    SERVICE_KEY VARCHAR(256) NOT NULL,
    MD5 VARCHAR(100) NOT NULL,
    TENANT_ID INTEGER NOT NULL,
    PRIMARY KEY (API_ID, SERVICE_KEY),
    FOREIGN KEY (API_ID) REFERENCES AM_API(API_ID) ON DELETE CASCADE
);


-- Gateway Environments Table --
DROP SEQUENCE IF EXISTS AM_GATEWAY_ENVIRONMENT_PK_SEQ;
CREATE SEQUENCE AM_GATEWAY_ENVIRONMENT_PK_SEQ;
CREATE TABLE IF NOT EXISTS AM_GATEWAY_ENVIRONMENT (
  ID INTEGER NOT NULL DEFAULT NEXTVAL('AM_GATEWAY_ENVIRONMENT_PK_SEQ'),
  UUID VARCHAR(45) NOT NULL,
  NAME VARCHAR(255) NOT NULL,
  TENANT_DOMAIN VARCHAR(255),
  DISPLAY_NAME VARCHAR(255) NULL,
  DESCRIPTION VARCHAR(1023) NULL,
  UNIQUE (NAME, TENANT_DOMAIN),
  UNIQUE (UUID),
  PRIMARY KEY (ID)
);

-- Virtual Hosts Table --
CREATE TABLE IF NOT EXISTS AM_GW_VHOST (
  GATEWAY_ENV_ID INTEGER,
  HOST VARCHAR(255) NOT NULL,
  HTTP_CONTEXT VARCHAR(255) NULL,
  HTTP_PORT VARCHAR(5) NOT NULL,
  HTTPS_PORT VARCHAR(5) NOT NULL,
  WS_PORT VARCHAR(5) NOT NULL,
  WSS_PORT VARCHAR(5) NOT NULL,
  FOREIGN KEY (GATEWAY_ENV_ID) REFERENCES AM_GATEWAY_ENVIRONMENT(ID) ON UPDATE CASCADE ON DELETE CASCADE,
  PRIMARY KEY (GATEWAY_ENV_ID, HOST)
);

ALTER TABLE AM_POLICY_SUBSCRIPTION ADD CONNECTIONS_COUNT INTEGER NOT NULL DEFAULT 0;

ALTER TABLE AM_API_COMMENTS RENAME COLUMN COMMENTED_USER TO CREATED_BY;
ALTER TABLE AM_API_COMMENTS RENAME COLUMN DATE_COMMENTED TO CREATED_TIME;
ALTER TABLE AM_API_COMMENTS ADD UPDATED_TIME TIMESTAMP;
ALTER TABLE AM_API_COMMENTS ADD PARENT_COMMENT_ID VARCHAR(255) DEFAULT NULL;
ALTER TABLE AM_API_COMMENTS ADD ENTRY_POINT VARCHAR(20)  DEFAULT 'DEVPORTAL';
ALTER TABLE AM_API_COMMENTS ADD CATEGORY VARCHAR(20) DEFAULT 'general';
ALTER TABLE AM_API_COMMENTS ADD FOREIGN KEY(PARENT_COMMENT_ID) REFERENCES AM_API_COMMENTS(COMMENT_ID);
ALTER TABLE AM_API ADD ORGANIZATION VARCHAR(100);

ALTER TABLE AM_APPLICATION ADD ORGANIZATION VARCHAR(100);

ALTER TABLE AM_API_CATEGORIES ADD ORGANIZATION VARCHAR(100);

ALTER TABLE AM_API_DEFAULT_VERSION ADD ORGANIZATION VARCHAR(100) NULL;

ALTER TABLE AM_GATEWAY_ENVIRONMENT ADD ORGANIZATION VARCHAR(100);

ALTER TABLE AM_GATEWAY_ENVIRONMENT ADD PROVIDER VARCHAR(255) DEFAULT 'wso2';

ALTER TABLE AM_API ADD LOG_LEVEL VARCHAR(255) DEFAULT 'OFF';

ALTER TABLE AM_API ADD VERSION_COMPARABLE VARCHAR(15);

ALTER TABLE AM_KEY_MANAGER ADD EXTERNAL_REFERENCE_ID VARCHAR(100) DEFAULT NULL;

ALTER TABLE AM_KEY_MANAGER RENAME COLUMN TENANT_DOMAIN TO ORGANIZATION;

ALTER TABLE AM_KEY_MANAGER ADD TOKEN_TYPE VARCHAR(45) DEFAULT 'DIRECT';

ALTER TABLE AM_API_REVISION_METADATA alter column REVISION_UUID type VARCHAR(255);

ALTER TABLE AM_BLOCK_CONDITIONS RENAME COLUMN VALUE TO BLOCK_CONDITION;

ALTER TABLE AM_APPLICATION_ATTRIBUTES RENAME COLUMN VALUE TO APP_ATTRIBUTE;

ALTER TABLE AM_API ADD GATEWAY_VENDOR VARCHAR(100) DEFAULT 'wso2';

CREATE TABLE IF NOT EXISTS AM_SYSTEM_CONFIGS
(
  ORGANIZATION     VARCHAR(100)            NOT NULL,
  CONFIG_TYPE      VARCHAR(100)            NOT NULL,
  CONFIGURATION    bytea                   NOT NULL,
  PRIMARY KEY (ORGANIZATION,CONFIG_TYPE)
);

CREATE TABLE IF NOT EXISTS AM_OPERATION_POLICY (
    POLICY_UUID VARCHAR(45) NOT NULL,
    POLICY_NAME VARCHAR(300) NOT NULL,
    POLICY_VERSION VARCHAR(45) DEFAULT 'v1',
    DISPLAY_NAME VARCHAR(300) NOT NULL,
    POLICY_DESCRIPTION VARCHAR(1024),
    APPLICABLE_FLOWS VARCHAR(45) NOT NULL,
    GATEWAY_TYPES VARCHAR(45) NOT NULL,
    API_TYPES VARCHAR(45) NOT NULL,
    POLICY_PARAMETERS bytea,
    ORGANIZATION VARCHAR(100),
    POLICY_CATEGORY VARCHAR(45) NOT NULL,
    POLICY_MD5 VARCHAR(45) NOT NULL,
    PRIMARY KEY(POLICY_UUID)
);

CREATE TABLE IF NOT EXISTS AM_OPERATION_POLICY_DEFINITION (
   DEFINITION_ID SERIAL,
   POLICY_UUID VARCHAR(45) NOT NULL,
   POLICY_DEFINITION bytea NOT NULL,
   GATEWAY_TYPE VARCHAR(20) NOT NULL,
   DEFINITION_MD5 VARCHAR(45) NOT NULL,
   UNIQUE (POLICY_UUID, GATEWAY_TYPE),
   FOREIGN KEY (POLICY_UUID) REFERENCES AM_OPERATION_POLICY(POLICY_UUID) ON DELETE CASCADE,
   PRIMARY KEY(DEFINITION_ID)
);

CREATE TABLE IF NOT EXISTS AM_COMMON_OPERATION_POLICY (
   COMMON_POLICY_ID SERIAL,
   POLICY_UUID VARCHAR(45) NOT NULL,
   FOREIGN KEY (POLICY_UUID) REFERENCES AM_OPERATION_POLICY(POLICY_UUID) ON DELETE CASCADE,
   PRIMARY KEY(COMMON_POLICY_ID)
);

CREATE TABLE IF NOT EXISTS AM_API_OPERATION_POLICY (
   API_SPECIFIC_POLICY_ID SERIAL,
   POLICY_UUID VARCHAR(45) NOT NULL,
   API_UUID VARCHAR(45) NOT NULL,
   REVISION_UUID VARCHAR(45),
   CLONED_POLICY_UUID VARCHAR(45),
   FOREIGN KEY (POLICY_UUID) REFERENCES AM_OPERATION_POLICY(POLICY_UUID) ON DELETE CASCADE,
   PRIMARY KEY(API_SPECIFIC_POLICY_ID)
);

CREATE TABLE IF NOT EXISTS AM_API_OPERATION_POLICY_MAPPING (
   OPERATION_POLICY_MAPPING_ID SERIAL,
   URL_MAPPING_ID INTEGER NOT NULL,
   POLICY_UUID VARCHAR(45) NOT NULL,
   POLICY_ORDER INTEGER NOT NULL,
   DIRECTION VARCHAR(10) NOT NULL,
   PARAMETERS VARCHAR(1024) NOT NULL,
   FOREIGN KEY (URL_MAPPING_ID) REFERENCES AM_API_URL_MAPPING(URL_MAPPING_ID) ON DELETE CASCADE,
   FOREIGN KEY (POLICY_UUID) REFERENCES AM_OPERATION_POLICY(POLICY_UUID) ON DELETE CASCADE,
   PRIMARY KEY(OPERATION_POLICY_MAPPING_ID)
);

CREATE TABLE IF NOT EXISTS AM_DEPLOYED_REVISION (
            NAME VARCHAR(255) NOT NULL,
            VHOST VARCHAR(255) NULL,
            REVISION_UUID VARCHAR(255) NOT NULL,
            DEPLOYED_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (NAME, REVISION_UUID),
            FOREIGN KEY (REVISION_UUID) REFERENCES AM_REVISION(REVISION_UUID) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS AM_API_ENVIRONMENT_KEYS
(
    UUID            VARCHAR(45)  NOT NULL,
    ENVIRONMENT_ID  VARCHAR(45)  NOT NULL,
    API_UUID        VARCHAR(256) NOT NULL,
    PROPERTY_CONFIG bytea DEFAULT NULL,
    UNIQUE (ENVIRONMENT_ID, API_UUID),
    FOREIGN KEY (API_UUID) REFERENCES AM_API (API_UUID) ON DELETE CASCADE,
    PRIMARY KEY (UUID)
);

commit;
