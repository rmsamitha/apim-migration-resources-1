CREATE TABLE IF NOT EXISTS AM_SYSTEM_APPS (
    ID INTEGER AUTO_INCREMENT,
    NAME VARCHAR(50) NOT NULL,
    CONSUMER_KEY VARCHAR(512) NOT NULL,
    CONSUMER_SECRET VARCHAR(512) NOT NULL,
    CREATED_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (NAME),
    UNIQUE (CONSUMER_KEY),
    PRIMARY KEY (ID)
 ) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS AM_API_CLIENT_CERTIFICATE (
  TENANT_ID INT(11) NOT NULL,
  ALIAS VARCHAR(45) NOT NULL,
  API_ID INTEGER NOT NULL,
  CERTIFICATE BLOB NOT NULL,
  REMOVED BOOLEAN NOT NULL DEFAULT 0,
  TIER_NAME VARCHAR (512),
  FOREIGN KEY (API_ID) REFERENCES AM_API (API_ID) ON DELETE CASCADE ON UPDATE CASCADE,
  PRIMARY KEY (ALIAS,TENANT_ID, REMOVED)
);

ALTER TABLE AM_POLICY_SUBSCRIPTION 
  ADD MONETIZATION_PLAN VARCHAR(25) NULL DEFAULT NULL, 
  ADD FIXED_RATE VARCHAR(15) NULL DEFAULT NULL, 
  ADD BILLING_CYCLE VARCHAR(15) NULL DEFAULT NULL, 
  ADD PRICE_PER_REQUEST VARCHAR(15) NULL DEFAULT NULL, 
  ADD CURRENCY VARCHAR(15) NULL DEFAULT NULL;

CREATE TABLE IF NOT EXISTS AM_MONETIZATION_USAGE_PUBLISHER (
	ID VARCHAR(100) NOT NULL,
	STATE VARCHAR(50) NOT NULL,
	STATUS VARCHAR(50) NOT NULL,
	STARTED_TIME VARCHAR(50) NOT NULL,
	PUBLISHED_TIME VARCHAR(50) NOT NULL,
	PRIMARY KEY(ID)
) ENGINE INNODB;

ALTER TABLE AM_API_COMMENTS
MODIFY COLUMN COMMENT_ID VARCHAR(255) NOT NULL;

ALTER TABLE AM_API_RATINGS
MODIFY COLUMN RATING_ID VARCHAR(255) NOT NULL;

CREATE TABLE IF NOT EXISTS AM_NOTIFICATION_SUBSCRIBER (
    UUID VARCHAR(255),
    CATEGORY VARCHAR(255),
    NOTIFICATION_METHOD VARCHAR(255),
    SUBSCRIBER_ADDRESS VARCHAR(255) NOT NULL,
    PRIMARY KEY(UUID, SUBSCRIBER_ADDRESS)
) ENGINE INNODB;

ALTER TABLE AM_EXTERNAL_STORES
ADD LAST_UPDATED_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE AM_API
  ADD API_TYPE VARCHAR(10) NULL DEFAULT NULL;

CREATE TABLE IF NOT EXISTS AM_API_PRODUCT_MAPPING (
  API_PRODUCT_MAPPING_ID INTEGER AUTO_INCREMENT,
  API_ID INTEGER,
  URL_MAPPING_ID INTEGER,
  FOREIGN KEY (API_ID) REFERENCES AM_API(API_ID) ON DELETE CASCADE,
  FOREIGN KEY (URL_MAPPING_ID) REFERENCES AM_API_URL_MAPPING(URL_MAPPING_ID) ON DELETE CASCADE,
  PRIMARY KEY(API_PRODUCT_MAPPING_ID)
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_REVOKED_JWT (
    UUID VARCHAR(255) NOT NULL,
    SIGNATURE VARCHAR(2048) NOT NULL,
    EXPIRY_TIMESTAMP BIGINT NOT NULL,
    TENANT_ID INTEGER DEFAULT -1,
    TOKEN_TYPE VARCHAR(15) DEFAULT 'DEFAULT',
    TIME_CREATED TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (UUID)
) ENGINE=InnoDB;

-- UMA tables --
CREATE TABLE IF NOT EXISTS IDN_UMA_RESOURCE (
  ID INTEGER AUTO_INCREMENT NOT NULL,
  RESOURCE_ID VARCHAR(255),
  RESOURCE_NAME VARCHAR(255),
  TIME_CREATED TIMESTAMP NOT NULL,
  RESOURCE_OWNER_NAME VARCHAR(255),
  CLIENT_ID VARCHAR(255),
  TENANT_ID INTEGER DEFAULT -1234,
  USER_DOMAIN VARCHAR(50),
  PRIMARY KEY (ID)
);

CREATE INDEX IDX_RID ON IDN_UMA_RESOURCE (RESOURCE_ID);

CREATE INDEX IDX_USER ON IDN_UMA_RESOURCE (RESOURCE_OWNER_NAME, USER_DOMAIN);

CREATE TABLE IF NOT EXISTS IDN_UMA_RESOURCE_META_DATA (
  ID INTEGER AUTO_INCREMENT NOT NULL,
  RESOURCE_IDENTITY INTEGER NOT NULL,
  PROPERTY_KEY VARCHAR(40),
  PROPERTY_VALUE VARCHAR(255),
  PRIMARY KEY (ID),
  FOREIGN KEY (RESOURCE_IDENTITY) REFERENCES IDN_UMA_RESOURCE (ID) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS IDN_UMA_RESOURCE_SCOPE (
  ID INTEGER AUTO_INCREMENT NOT NULL,
  RESOURCE_IDENTITY INTEGER NOT NULL,
  SCOPE_NAME VARCHAR(255),
  PRIMARY KEY (ID),
  FOREIGN KEY (RESOURCE_IDENTITY) REFERENCES IDN_UMA_RESOURCE (ID) ON DELETE CASCADE
);

CREATE INDEX IDX_RS ON IDN_UMA_RESOURCE_SCOPE (SCOPE_NAME);

CREATE TABLE IF NOT EXISTS IDN_UMA_PERMISSION_TICKET (
  ID INTEGER AUTO_INCREMENT NOT NULL,
  PT VARCHAR(255) NOT NULL,
  TIME_CREATED TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  EXPIRY_TIME TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  TICKET_STATE VARCHAR(25) DEFAULT 'ACTIVE',
  TENANT_ID INTEGER DEFAULT -1234,
  PRIMARY KEY (ID)
);

CREATE INDEX IDX_PT ON IDN_UMA_PERMISSION_TICKET (PT);

CREATE TABLE IF NOT EXISTS IDN_UMA_PT_RESOURCE (
  ID INTEGER AUTO_INCREMENT NOT NULL,
  PT_RESOURCE_ID INTEGER NOT NULL,
  PT_ID INTEGER NOT NULL,
  PRIMARY KEY (ID),
  FOREIGN KEY (PT_ID) REFERENCES IDN_UMA_PERMISSION_TICKET (ID) ON DELETE CASCADE,
  FOREIGN KEY (PT_RESOURCE_ID) REFERENCES IDN_UMA_RESOURCE (ID) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS IDN_UMA_PT_RESOURCE_SCOPE (
  ID INTEGER AUTO_INCREMENT NOT NULL,
  PT_RESOURCE_ID INTEGER NOT NULL,
  PT_SCOPE_ID INTEGER NOT NULL,
  PRIMARY KEY (ID),
  FOREIGN KEY (PT_RESOURCE_ID) REFERENCES IDN_UMA_PT_RESOURCE (ID) ON DELETE CASCADE,
  FOREIGN KEY (PT_SCOPE_ID) REFERENCES IDN_UMA_RESOURCE_SCOPE (ID) ON DELETE CASCADE
);

ALTER TABLE AM_API ADD API_UUID VARCHAR(255);
CREATE TABLE IF NOT EXISTS AM_API_CATEGORIES (
  UUID VARCHAR(50),
  NAME VARCHAR(255),
  DESCRIPTION VARCHAR(1024),
  TENANT_ID INTEGER DEFAULT -1,
  UNIQUE (NAME,TENANT_ID),
  PRIMARY KEY (UUID)
) ENGINE=InnoDB;

ALTER TABLE AM_SYSTEM_APPS
ADD TENANT_DOMAIN VARCHAR(255) DEFAULT 'carbon.super';

ALTER TABLE AM_SYSTEM_APPS
DROP INDEX NAME;

CREATE TABLE IF NOT EXISTS AM_USER (
    USER_ID VARCHAR(255) NOT NULL,
    USER_NAME VARCHAR(255) NOT NULL,
    PRIMARY KEY(USER_ID)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS AM_SECURITY_AUDIT_UUID_MAPPING (
    API_ID INTEGER NOT NULL,
    AUDIT_UUID VARCHAR(255) NOT NULL,
    PRIMARY KEY (API_ID),
    FOREIGN KEY (API_ID) REFERENCES AM_API(API_ID)
)ENGINE INNODB;

DELETE FROM IDN_OAUTH2_SCOPE_BINDING WHERE SCOPE_BINDING IS NULL OR SCOPE_BINDING = '';

ALTER TABLE AM_API ADD API_UUID VARCHAR(255);
ALTER TABLE AM_WORKFLOWS ADD
WF_METADATA VARBINARY(MAX) NULL DEFAULT NULL,
WF_PROPERTIES VARBINARY(MAX) NULL DEFAULT NULL
;

IF NOT EXISTS (SELECT * FROM SYS.OBJECTS WHERE OBJECT_ID = OBJECT_ID(N'[DBO].[AM_GW_PUBLISHED_API_DETAILS]') AND TYPE IN (N'U'))
CREATE TABLE AM_GW_PUBLISHED_API_DETAILS (
  API_ID varchar(255) NOT NULL,
  TENANT_DOMAIN varchar(255),
  API_PROVIDER varchar(255),
  API_NAME varchar(255),
  API_VERSION varchar(255),
  PRIMARY KEY (API_ID)
);

IF NOT EXISTS (SELECT * FROM SYS.OBJECTS WHERE OBJECT_ID = OBJECT_ID(N'[DBO].[AM_GW_API_ARTIFACTS]') AND TYPE IN (N'U'))
CREATE TABLE AM_GW_API_ARTIFACTS (
  API_ID varchar(255) NOT NULL,
  ARTIFACT VARBINARY(MAX),
  GATEWAY_INSTRUCTION varchar(20),
  GATEWAY_LABEL varchar(255),
  TIMESTAMP DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (GATEWAY_LABEL, API_ID),
  FOREIGN KEY (API_ID) REFERENCES AM_GW_PUBLISHED_API_DETAILS(API_ID) ON UPDATE CASCADE ON DELETE NO ACTION
);

GO
CREATE TRIGGER dbo.TIMESTAMP ON dbo.AM_GW_API_ARTIFACTS
AFTER INSERT, UPDATE
  AS
  UPDATE f set TIMESTAMP=GETDATE()
  FROM
  dbo.[AM_GW_API_ARTIFACTS] AS f
  INNER JOIN inserted
  AS i
  ON f.TIMESTAMP = i.TIMESTAMP;
GO

ALTER TABLE AM_SUBSCRIPTION ADD TIER_ID_PENDING VARCHAR(50);

ALTER TABLE AM_POLICY_SUBSCRIPTION ADD
  MAX_COMPLEXITY INTEGER NOT NULL DEFAULT 0,
  MAX_DEPTH INTEGER NOT NULL DEFAULT 0
;

IF NOT EXISTS (SELECT * FROM SYS.OBJECTS WHERE OBJECT_ID = OBJECT_ID(N'[DBO].[AM_API_RESOURCE_SCOPE_MAPPING]') AND TYPE IN (N'U'))
CREATE TABLE AM_API_RESOURCE_SCOPE_MAPPING (
    SCOPE_NAME VARCHAR(255) NOT NULL,
    URL_MAPPING_ID INTEGER NOT NULL,
    TENANT_ID INTEGER NOT NULL,
    FOREIGN KEY (URL_MAPPING_ID) REFERENCES   AM_API_URL_MAPPING(URL_MAPPING_ID) ON DELETE CASCADE,
    PRIMARY KEY(SCOPE_NAME, URL_MAPPING_ID)
);

IF NOT EXISTS (SELECT * FROM SYS.OBJECTS WHERE OBJECT_ID = OBJECT_ID(N'[DBO].[AM_SHARED_SCOPE]') AND TYPE IN (N'U'))
CREATE TABLE AM_SHARED_SCOPE (
    NAME VARCHAR(255),
    UUID VARCHAR (256),
    TENANT_ID INTEGER,
    PRIMARY KEY (UUID)
);

DECLARE @SQL VARCHAR(4000);
SET @SQL = 'ALTER TABLE |TABLE_NAME| DROP CONSTRAINT |CONSTRAINT_NAME|';

SET @SQL = REPLACE(@SQL, '|CONSTRAINT_NAME|',( SELECT name FROM sysobjects WHERE xtype = 'PK' AND parent_obj = OBJECT_ID('IDN_OAUTH2_RESOURCE_SCOPE')));
SET @SQL = REPLACE(@SQL,'|TABLE_NAME|','IDN_OAUTH2_RESOURCE_SCOPE');
EXEC (@SQL);

IF NOT EXISTS (SELECT * FROM SYS.OBJECTS WHERE OBJECT_ID = OBJECT_ID(N'[DBO].[AM_KEY_MANAGER]') AND TYPE IN (N'U'))
CREATE TABLE AM_KEY_MANAGER (
  UUID VARCHAR(50) NOT NULL,
  NAME VARCHAR(100) NULL,
  DISPLAY_NAME VARCHAR(100) NULL,
  DESCRIPTION VARCHAR(256) NULL,
  TYPE VARCHAR(45) NULL,
  CONFIGURATION VARBINARY(MAX) NULL,
  ENABLED BIT DEFAULT 1,
  TENANT_DOMAIN VARCHAR(100) NULL,
  PRIMARY KEY (UUID),
  UNIQUE (NAME,TENANT_DOMAIN)
);

IF NOT EXISTS (SELECT * FROM SYS.OBJECTS WHERE OBJECT_ID = OBJECT_ID(N'[DBO].[AM_TENANT_THEMES]') AND TYPE IN (N'U'))
CREATE TABLE AM_TENANT_THEMES (
  TENANT_ID INTEGER NOT NULL,
  THEME VARBINARY(MAX) NOT NULL,
  PRIMARY KEY (TENANT_ID)
);

IF NOT EXISTS (SELECT * FROM SYS.OBJECTS WHERE OBJECT_ID = OBJECT_ID(N'[DBO].[AM_GRAPHQL_COMPLEXITY]') AND TYPE IN (N'U'))
CREATE TABLE AM_GRAPHQL_COMPLEXITY (
    UUID VARCHAR(256),
    API_ID INTEGER NOT NULL,
    TYPE VARCHAR(256),
    FIELD VARCHAR(256),
    COMPLEXITY_VALUE INTEGER,
    FOREIGN KEY (API_ID) REFERENCES AM_API(API_ID) ON UPDATE CASCADE ON DELETE CASCADE,
    PRIMARY KEY(UUID),
    UNIQUE (API_ID,TYPE,FIELD)
);

UPDATE IDN_OAUTH_CONSUMER_APPS SET CALLBACK_URL='' WHERE CALLBACK_URL IS NULL;

ALTER TABLE AM_APPLICATION_KEY_MAPPING ADD UUID VARCHAR(50);
GO
UPDATE AM_APPLICATION_KEY_MAPPING SET UUID = NEWID() WHERE UUID IS NULL;
GO
ALTER TABLE AM_APPLICATION_KEY_MAPPING ADD KEY_MANAGER VARCHAR(50) NOT NULL DEFAULT 'Resident Key Manager';
ALTER TABLE AM_APPLICATION_KEY_MAPPING ADD APP_INFO VARBINARY;
ALTER TABLE AM_APPLICATION_KEY_MAPPING ADD CONSTRAINT app_key_unique_cns UNIQUE (APPLICATION_ID,KEY_TYPE,KEY_MANAGER);
DECLARE @ap_keymap as VARCHAR(8000);
SET @ap_keymap = (SELECT name from sys.objects where parent_object_id=object_id('AM_APPLICATION_KEY_MAPPING') AND type='PK');
EXEC('ALTER TABLE AM_APPLICATION_KEY_MAPPING
drop CONSTRAINT ' + @ap_keymap);

DECLARE @am_appreg as VARCHAR(8000);
SET @am_appreg = (SELECT name from sys.objects where parent_object_id=object_id('AM_APPLICATION_REGISTRATION') AND type='UQ');
EXEC('ALTER TABLE AM_APPLICATION_REGISTRATION
drop CONSTRAINT ' + @am_appreg);

ALTER TABLE AM_APPLICATION_REGISTRATION ADD KEY_MANAGER VARCHAR(255) DEFAULT 'Resident Key Manager';
ALTER TABLE AM_APPLICATION_REGISTRATION ADD UNIQUE (SUBSCRIBER_ID,APP_ID,TOKEN_TYPE,KEY_MANAGER); 

IF NOT EXISTS (SELECT * FROM SYS.OBJECTS WHERE OBJECT_ID = OBJECT_ID(N'[DBO].[AM_SCOPE]') AND TYPE IN (N'U'))
CREATE TABLE AM_SCOPE (
  SCOPE_ID INTEGER IDENTITY,
  NAME VARCHAR(255) NOT NULL,
  DISPLAY_NAME VARCHAR(255) NOT NULL,
  DESCRIPTION VARCHAR(512),
  TENANT_ID INTEGER NOT NULL DEFAULT -1,
  SCOPE_TYPE VARCHAR(255) NOT NULL,
  PRIMARY KEY (SCOPE_ID)
);

IF NOT  EXISTS (SELECT * FROM SYS.OBJECTS WHERE OBJECT_ID = OBJECT_ID(N'[DBO].[AM_SCOPE_BINDING]') AND TYPE IN (N'U'))
CREATE TABLE AM_SCOPE_BINDING (
  SCOPE_ID INTEGER NOT NULL,
  SCOPE_BINDING VARCHAR(255) NOT NULL,
  BINDING_TYPE VARCHAR(255) NOT NULL,
  FOREIGN KEY (SCOPE_ID) REFERENCES AM_SCOPE(SCOPE_ID) ON DELETE CASCADE
);
ALTER TABLE AM_API ADD API_UUID VARCHAR(255);
ALTER TABLE AM_API ADD STATUS VARCHAR(30);
ALTER TABLE AM_API ADD REVISIONS_CREATED INTEGER DEFAULT 0;
ALTER TABLE AM_CERTIFICATE_METADATA ADD CERTIFICATE BLOB DEFAULT NULL;

CREATE TABLE IF NOT EXISTS AM_REVISION (
  ID INTEGER NOT NULL,
  API_UUID VARCHAR(256) NOT NULL,
  REVISION_UUID VARCHAR(255) NOT NULL,
  DESCRIPTION VARCHAR(255),
  CREATED_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CREATED_BY VARCHAR(255),
  PRIMARY KEY (ID, API_UUID),
  UNIQUE(REVISION_UUID)
)ENGINE INNODB;


CREATE TABLE IF NOT EXISTS AM_API_REVISION_METADATA (
    API_UUID VARCHAR(64),
    REVISION_UUID VARCHAR(64),
    API_TIER VARCHAR(128),
    UNIQUE (API_UUID,REVISION_UUID)
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_DEPLOYMENT_REVISION_MAPPING (
  NAME VARCHAR(255) NOT NULL,
  VHOST VARCHAR(255) NULL,
  REVISION_UUID VARCHAR(255) NOT NULL,
  DISPLAY_ON_DEVPORTAL BOOLEAN DEFAULT 0,
  DEPLOYED_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (NAME, REVISION_UUID),
  FOREIGN KEY (REVISION_UUID) REFERENCES AM_REVISION(REVISION_UUID) ON UPDATE CASCADE ON DELETE CASCADE
)ENGINE INNODB;

ALTER TABLE AM_API_CLIENT_CERTIFICATE ADD REVISION_UUID VARCHAR(255) NOT NULL DEFAULT 'Current API';
ALTER TABLE AM_API_CLIENT_CERTIFICATE DROP PRIMARY KEY;
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
)ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS AM_GW_API_ARTIFACTS (
  API_ID VARCHAR(255) NOT NULL,
  REVISION_ID VARCHAR(255) NOT NULL,
  ARTIFACT blob,
  TIME_STAMP TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (REVISION_ID, API_ID),
  FOREIGN KEY (API_ID) REFERENCES AM_GW_PUBLISHED_API_DETAILS(API_ID) ON UPDATE CASCADE ON DELETE NO ACTION
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS AM_GW_API_DEPLOYMENTS (
  API_ID VARCHAR(255) NOT NULL,
  REVISION_ID VARCHAR(255) NOT NULL,
  LABEL VARCHAR(255) NOT NULL,
  VHOST VARCHAR(255) NULL,
  PRIMARY KEY (REVISION_ID, API_ID,LABEL),
  FOREIGN KEY (API_ID) REFERENCES AM_GW_PUBLISHED_API_DETAILS(API_ID) ON UPDATE CASCADE ON DELETE NO ACTION
) ENGINE=InnoDB;

-- Service Catalog --
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
            MUTUAL_SSL_ENABLED BOOLEAN DEFAULT 0,
            CREATED_TIME TIMESTAMP NULL,
            LAST_UPDATED_TIME TIMESTAMP NULL,
            CREATED_BY VARCHAR(255),
            UPDATED_BY VARCHAR(255),
            SERVICE_DEFINITION BLOB NOT NULL,
            METADATA BLOB NOT NULL,
            PRIMARY KEY (UUID),
            UNIQUE (SERVICE_NAME, SERVICE_VERSION, TENANT_ID),
            UNIQUE (SERVICE_KEY, TENANT_ID)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS AM_API_SERVICE_MAPPING (
    API_ID INTEGER NOT NULL,
    SERVICE_KEY VARCHAR(256) NOT NULL,
    MD5 VARCHAR(100) NOT NULL,
    TENANT_ID INTEGER NOT NULL,
    PRIMARY KEY (API_ID, SERVICE_KEY),
    FOREIGN KEY (API_ID) REFERENCES AM_API(API_ID) ON DELETE CASCADE
)ENGINE=InnoDB;

-- Webhooks --
CREATE TABLE IF NOT EXISTS AM_WEBHOOKS_SUBSCRIPTION (
            WH_SUBSCRIPTION_ID INTEGER NOT NULL AUTO_INCREMENT,
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
            DELIVERY_STATE TINYINT(1),
            PRIMARY KEY (WH_SUBSCRIPTION_ID)
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_WEBHOOKS_UNSUBSCRIPTION (
            API_UUID VARCHAR(255) NOT NULL,
            APPLICATION_ID VARCHAR(20) NOT NULL,
            TENANT_DOMAIN VARCHAR(255) NOT NULL,
            HUB_CALLBACK_URL VARCHAR(1024) NOT NULL,
            HUB_TOPIC VARCHAR(255) NOT NULL,
            HUB_SECRET VARCHAR(2048),
            HUB_LEASE_SECONDS INTEGER,
            ADDED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)ENGINE INNODB;

-- Gateway Environments Table --
CREATE TABLE IF NOT EXISTS AM_GATEWAY_ENVIRONMENT (
  ID INTEGER NOT NULL AUTO_INCREMENT,
  UUID VARCHAR(45) NOT NULL,
  NAME VARCHAR(255) NOT NULL,
  TENANT_DOMAIN VARCHAR(255),
  DISPLAY_NAME VARCHAR(255) NULL,
  DESCRIPTION VARCHAR(1023) NULL,
  UNIQUE (NAME, TENANT_DOMAIN),
  UNIQUE (UUID),
  PRIMARY KEY (ID)
)ENGINE INNODB;

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
)ENGINE INNODB;

ALTER TABLE AM_POLICY_SUBSCRIPTION ADD CONNECTIONS_COUNT INT(11) NOT NULL DEFAULT 0;

ALTER TABLE AM_API_COMMENTS CHANGE COMMENT_ID COMMENT_ID VARCHAR(64);
ALTER TABLE AM_API_COMMENTS CHANGE COMMENTED_USER CREATED_BY VARCHAR(512);
ALTER TABLE AM_API_COMMENTS CHANGE DATE_COMMENTED CREATED_TIME TIMESTAMP NOT NULL;
ALTER TABLE AM_API_COMMENTS ADD UPDATED_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE AM_API_COMMENTS ADD PARENT_COMMENT_ID VARCHAR(64) DEFAULT NULL;
ALTER TABLE AM_API_COMMENTS ADD ENTRY_POINT VARCHAR(20)  DEFAULT 'DEVPORTAL';
ALTER TABLE AM_API_COMMENTS ADD CATEGORY VARCHAR(20) DEFAULT 'general';
ALTER TABLE AM_API_COMMENTS ADD FOREIGN KEY(PARENT_COMMENT_ID) REFERENCES AM_API_COMMENTS(COMMENT_ID);
ALTER TABLE AM_API ADD ORGANIZATION VARCHAR(100);
ALTER TABLE AM_APPLICATION ADD ORGANIZATION VARCHAR(100);
ALTER TABLE AM_API_CATEGORIES ADD ORGANIZATION VARCHAR(100);
ALTER TABLE AM_API_DEFAULT_VERSION ADD ORGANIZATION VARCHAR(100);
ALTER TABLE AM_GATEWAY_ENVIRONMENT ADD ORGANIZATION VARCHAR(100);
ALTER TABLE AM_GATEWAY_ENVIRONMENT ADD PROVIDER VARCHAR(255) DEFAULT 'wso2';
ALTER TABLE AM_API ADD GATEWAY_VENDOR VARCHAR(100) DEFAULT 'wso2';
ALTER TABLE AM_API ADD LOG_LEVEL VARCHAR(255) DEFAULT 'OFF';
ALTER TABLE AM_API ADD VERSION_COMPARABLE VARCHAR(15);
ALTER TABLE AM_KEY_MANAGER ADD TOKEN_TYPE VARCHAR(45) DEFAULT "DIRECT";
ALTER TABLE AM_KEY_MANAGER ADD EXTERNAL_REFERENCE_ID VARCHAR(100) DEFAULT NULL;
ALTER TABLE AM_KEY_MANAGER RENAME COLUMN TENANT_DOMAIN TO ORGANIZATION;
ALTER TABLE AM_API_REVISION_METADATA MODIFY COLUMN REVISION_UUID VARCHAR(255);
ALTER TABLE AM_GW_API_ARTIFACTS MODIFY COLUMN ARTIFACT MEDIUMBLOB;

CREATE TABLE IF NOT EXISTS AM_SYSTEM_CONFIGS
(
  ORGANIZATION     VARCHAR(100)            NOT NULL,
  CONFIG_TYPE      VARCHAR(100)            NOT NULL,
  CONFIGURATION    BLOB                    NOT NULL,
  PRIMARY KEY (ORGANIZATION,CONFIG_TYPE)
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_OPERATION_POLICY (
    POLICY_UUID VARCHAR(45) NOT NULL,
    POLICY_NAME VARCHAR(300) NOT NULL,
    POLICY_VERSION VARCHAR(45) DEFAULT 'v1',
    DISPLAY_NAME VARCHAR(300) NOT NULL,
    POLICY_DESCRIPTION VARCHAR(1024),
    APPLICABLE_FLOWS VARCHAR(45) NOT NULL,
    GATEWAY_TYPES VARCHAR(45) NOT NULL,
    API_TYPES VARCHAR(45) NOT NULL,
    POLICY_PARAMETERS blob,
    ORGANIZATION VARCHAR(100),
    POLICY_CATEGORY VARCHAR(45) NOT NULL,
    POLICY_MD5 VARCHAR(45) NOT NULL,
    PRIMARY KEY(POLICY_UUID)
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_OPERATION_POLICY_DEFINITION (
   DEFINITION_ID INTEGER AUTO_INCREMENT,
   POLICY_UUID VARCHAR(45) NOT NULL,
   POLICY_DEFINITION blob NOT NULL,
   GATEWAY_TYPE VARCHAR(20) NOT NULL,
   DEFINITION_MD5 VARCHAR(45) NOT NULL,
   UNIQUE (POLICY_UUID, GATEWAY_TYPE),
   FOREIGN KEY (POLICY_UUID) REFERENCES AM_OPERATION_POLICY(POLICY_UUID) ON DELETE CASCADE,
   PRIMARY KEY(DEFINITION_ID)
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_COMMON_OPERATION_POLICY (
   COMMON_POLICY_ID INTEGER AUTO_INCREMENT,
   POLICY_UUID VARCHAR(45) NOT NULL,
   FOREIGN KEY (POLICY_UUID) REFERENCES AM_OPERATION_POLICY(POLICY_UUID) ON DELETE CASCADE,
   PRIMARY KEY(COMMON_POLICY_ID)
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_API_OPERATION_POLICY (
   API_SPECIFIC_POLICY_ID INTEGER AUTO_INCREMENT,
   POLICY_UUID VARCHAR(45) NOT NULL,
   API_UUID VARCHAR(45) NOT NULL,
   REVISION_UUID VARCHAR(45),
   CLONED_POLICY_UUID VARCHAR(45),
   FOREIGN KEY (POLICY_UUID) REFERENCES AM_OPERATION_POLICY(POLICY_UUID) ON DELETE CASCADE,
   PRIMARY KEY(API_SPECIFIC_POLICY_ID)
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_API_OPERATION_POLICY_MAPPING (
   OPERATION_POLICY_MAPPING_ID INTEGER AUTO_INCREMENT,
   URL_MAPPING_ID INTEGER NOT NULL,
   POLICY_UUID VARCHAR(45) NOT NULL,
   POLICY_ORDER INTEGER NOT NULL,
   DIRECTION VARCHAR(10) NOT NULL,
   PARAMETERS VARCHAR(1024) NOT NULL,
   FOREIGN KEY (URL_MAPPING_ID) REFERENCES AM_API_URL_MAPPING(URL_MAPPING_ID) ON DELETE CASCADE,
   FOREIGN KEY (POLICY_UUID) REFERENCES AM_OPERATION_POLICY(POLICY_UUID) ON DELETE CASCADE,
   PRIMARY KEY(OPERATION_POLICY_MAPPING_ID)
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_DEPLOYED_REVISION (
  NAME VARCHAR(255) NOT NULL,
  VHOST VARCHAR(255) NULL,
  REVISION_UUID VARCHAR(255) NOT NULL,
  DEPLOYED_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (NAME, REVISION_UUID),
  FOREIGN KEY (REVISION_UUID) REFERENCES AM_REVISION(REVISION_UUID) ON UPDATE CASCADE ON DELETE CASCADE
)ENGINE INNODB;

CREATE TABLE IF NOT EXISTS AM_API_ENVIRONMENT_KEYS
(
    UUID            VARCHAR(45)  NOT NULL,
    ENVIRONMENT_ID  VARCHAR(45)  NOT NULL,
    API_UUID          VARCHAR(256) NOT NULL,
    PROPERTY_CONFIG BLOB DEFAULT NULL,
    UNIQUE (ENVIRONMENT_ID, API_UUID),
    FOREIGN KEY (API_UUID) REFERENCES AM_API(API_UUID) ON DELETE CASCADE,
    PRIMARY KEY (UUID)
)ENGINE INNODB;

--Changes introduced with H2 upgrade--
ALTER TABLE AM_BLOCK_CONDITIONS RENAME COLUMN VALUE TO BLOCK_CONDITION;
ALTER TABLE AM_APPLICATION_ATTRIBUTES RENAME COLUMN VALUE TO APP_ATTRIBUTE;
