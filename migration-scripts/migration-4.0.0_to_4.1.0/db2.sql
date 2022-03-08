ALTER TABLE AM_API ADD ORGANIZATION VARCHAR(100) /
ALTER TABLE AM_APPLICATION ADD ORGANIZATION VARCHAR(100) /
ALTER TABLE AM_API_CATEGORIES ADD ORGANIZATION VARCHAR(100) /

CREATE TABLE AM_SYSTEM_CONFIGS
(
  ORGANIZATION     VARCHAR(100)            NOT NULL,
  CONFIG_TYPE      VARCHAR(100)            NOT NULL,
  CONFIGURATION    BLOB                    NOT NULL,
  PRIMARY KEY (ORGANIZATION,CONFIG_TYPE)
)
/
ALTER TABLE AM_API ADD VERSION_TIMESTAMP VARCHAR(15) /
--Changes introduced with H2 upgrade--
ALTER TABLE AM_BLOCK_CONDITIONS RENAME COLUMN VALUE TO BLOCK_CONDITION /
ALTER TABLE AM_APPLICATION_ATTRIBUTES RENAME COLUMN VALUE TO APP_ATTRIBUTE /
