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

package org.wso2.carbon.apimgt.migration.migrator.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.migrator.VersionMigrator;
import org.wso2.carbon.apimgt.migration.migrator.commonMigrators.PreDBScriptMigrator;
import org.wso2.carbon.apimgt.migration.migrator.commonMigrators.RegistryResourceMigrator;
import org.wso2.carbon.apimgt.migration.migrator.v320.V320DBDataMigrator;
import org.wso2.carbon.apimgt.migration.migrator.v320.IdentityScopeMigrator;
import org.wso2.carbon.apimgt.migration.migrator.v320.SPMigrator;
import org.wso2.carbon.apimgt.migration.migrator.v320.ScopeMigrator;
import org.wso2.carbon.apimgt.migration.migrator.v320.V320RegistryResourceMigrator;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.user.api.UserStoreException;

public class V320Migration extends VersionMigrator {
    private static final Log log = LogFactory.getLog(V320Migration.class);

    @Override
    public String getPreviousVersion() {
        return "3.1.0";
    }

    @Override
    public String getCurrentVersion() {
        return "3.2.0";
    }

    @Override
    public void migrate() throws APIMigrationException, UserStoreException {
        log.info("--------------------------------------------------------------------------------------------------");
        log.info("WSO2 API-M Migration Task : Starting migration from " + getPreviousVersion() + " to "
                + getCurrentVersion() + "...");
        log.info("--------------------------------------------------------------------------------------------------");
        log.info("WSO2 API-M Migration Task : Starting AM_DB schema migration from 3.1.0 to 3.2.0");
        PreDBScriptMigrator preDBScriptMigrator = new PreDBScriptMigrator(Constants.V320_PRE_MIGRATION_SCRIPTS_PATH);
        preDBScriptMigrator.run();
        log.info("WSO2 API-M Migration Task : Completed AM_DB schema migration from 3.1.0 to 3.2.0");

        log.info("WSO2 API-M Migration Task : Starting AM_DB data migration from 3.1.0 to 3.2.0");
        V320DBDataMigrator dbDataMigrator = new V320DBDataMigrator();
        dbDataMigrator.migrate();
        log.info("WSO2 API-M Migration Task : Completed AM_DB data migration from 3.1.0 to 3.2.0");

        log.info("WSO2 API-M Migration Task : Starting registry resource migration from 3.1.0 to 3.2.0");
        RegistryResourceMigrator registryResourceMigrator = new V320RegistryResourceMigrator(Constants.V320_RXT_PATH);
        registryResourceMigrator.migrate();
        log.info("WSO2 API-M Migration Task : Completed registry resource migration from 3.1.0 to 3.2.0");

        log.info("WSO2 API-M Migration Task : Starting scope migration from 3.1.0 to 3.2.0");
        ScopeMigrator scopeMigrator = new ScopeMigrator();
        scopeMigrator.migrate();
        log.info("WSO2 API-M Migration Task : Completed scope migration from 3.1.0 to 3.2.0");

        log.info("WSO2 API-M Migration Task : Starting service provider migration from 3.1.0 to 3.2.0");
        SPMigrator spMigrator = new SPMigrator();
        spMigrator.migrate();
        log.info("WSO2 API-M Migration Task : Completed service provider migration from 3.1.0 to 3.2.0");

        log.info("WSO2 API-M Migration Task : Starting identity scope migration from 3.1.0 to 3.2.0");
        IdentityScopeMigrator identityScopeMigrator = new IdentityScopeMigrator();
        identityScopeMigrator.migrate();
        log.info("WSO2 API-M Migration Task : Completed identity scope migration from 3.1.0 to 3.2.0");

        log.info("WSO2 API-M Migration Task : Completed migration from " + getPreviousVersion() + " to " + getCurrentVersion() + "...");
    }


}