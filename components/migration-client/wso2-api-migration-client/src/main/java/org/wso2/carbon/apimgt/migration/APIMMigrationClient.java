/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.apimgt.migration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.migration.migrator.VersionMigrationHolder;
import org.wso2.carbon.apimgt.migration.migrator.VersionMigrator;
import org.wso2.carbon.apimgt.migration.migrator.commonMigrators.ArtifactReIndexingMigrator;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.SharedDBUtil;
import org.wso2.carbon.apimgt.migration.validator.ValidationHandler;
import org.wso2.carbon.core.ServerStartupObserver;
import org.wso2.carbon.user.api.UserStoreException;
import java.util.List;

public class APIMMigrationClient implements ServerStartupObserver {
    private static final Log log = LogFactory.getLog(APIMMigrationClient.class);

    @Override
    public void completingServerStartup() {

    }

    @Override
    public void completedServerStartup() {
        try {
            APIMgtDBUtil.initialize();
            SharedDBUtil.initialize();
        } catch (Exception e) {
            log.error("WSO2 API-M Migration Task : Error occurred while initializing DB Util ", e);
        }

        String migrateFromVersion = System.getProperty(Constants.ARG_MIGRATE_FROM_VERSION);
        String migratedVersion = System.getProperty(Constants.ARG_MIGRATED_VERSION);
        String preMigrationStep = System.getProperty(Constants.PRE_MIGRATION_STEP);

        if (preMigrationStep != null) {
            ValidationHandler validationHandler = new ValidationHandler(migrateFromVersion);
            try {
                validationHandler.doValidation();
            } catch (UserStoreException | APIMigrationException e) {
                log.error("WSO2 API-M Migration Task : Error while running the pre-migration validation", e);
            }
        } else {
            String originalMigrateFromVersion = migrateFromVersion;
            try {
                executeMigration(migrateFromVersion, migratedVersion);
            } catch (APIMigrationException e) {
                log.error("WSO2 API-M Migration Task : API Migration exception occurred while migrating", e);
            }
            ArtifactReIndexingMigrator artifactReIndexingMigrator = new ArtifactReIndexingMigrator();
            try {
                artifactReIndexingMigrator.migrate();
            } catch (APIMigrationException e) {
                log.error("WSO2 API-M Migration Task : Error running the artifact re-indexing script", e);
            }
            log.info("WSO2 API-M Migration Task : Successfully completed API-M migration from " +
                    originalMigrateFromVersion + " to " + migratedVersion);
        }
    }

    private void executeMigration(String migrateFromVersion, String migratedVersion) throws APIMigrationException {
        VersionMigrationHolder versionMigrationHolder = VersionMigrationHolder.getInstance();
        List<VersionMigrator> versionMigrationList = versionMigrationHolder.getVersionMigrationList();
        log.info("WSO2 API-M Migration Task : Starting API-M migration from " + migrateFromVersion + " to " +
                migrateFromVersion);
        for (VersionMigrator versionMigration : versionMigrationList) {
            if (versionMigration.getPreviousVersion().equals(migrateFromVersion)) {
                try {
                    versionMigration.migrate();
                } catch (APIMigrationException | UserStoreException e) {
                    throw new APIMigrationException("WSO2 API-M Migration Task : Error while executing migration from "
                            + "API-Manager " + migrateFromVersion + ". ", e);
                }
                migrateFromVersion = versionMigration.getCurrentVersion();
                if (versionMigration.getCurrentVersion().equals(migratedVersion)) {
                    break;
                }
            }
        }
    }
}