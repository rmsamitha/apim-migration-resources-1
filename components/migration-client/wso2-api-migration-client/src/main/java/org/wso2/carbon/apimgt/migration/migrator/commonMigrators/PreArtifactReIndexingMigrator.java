/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.apimgt.migration.migrator.commonMigrators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.migrator.Migrator;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.DBUtil;
import org.wso2.carbon.apimgt.migration.util.RegDBUtil;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

/**
 * Class to run artifact re-indexing scripts post-migration.
 */
public class PreArtifactReIndexingMigrator extends Migrator {
    private static final Log log = LogFactory.getLog(PreArtifactReIndexingMigrator.class);

    @Override
    public void migrate() throws APIMigrationException {
        log.info("WSO2 API-M Migration Task : Pre Artifact re-indexing migrator to clear REG_LOG table started");
        try {
            Map<String, DataSource> dataSourceMap = RegDBUtil.getDataSources();
            for (Map.Entry<String, DataSource> dataSource : dataSourceMap.entrySet()) {
                log.info("WSO2 API-M Migration Task : Executing the PreArtifactReIndexingMigrator for REG_DB.REG_LOG "
                        + "table cleanup SQL script at " + Constants.ARTIFACT_REINDEXING_SCRIPT_PATH
                        + " for registry datasource: " + dataSource.getKey());
                DBUtil.runSQLScript(Constants.ARTIFACT_REINDEXING_SCRIPT_PATH, false,
                        dataSource.getValue().getConnection());
                log.info("WSO2 API-M Migration Task : Successfully executed the PreArtifactReIndexingMigrator for "
                        + "REG_DB.REG_LOG table cleanup SQL script at " + Constants.ARTIFACT_REINDEXING_SCRIPT_PATH
                        + " for registry datasource: " + dataSource.getKey());
            }
        } catch (SQLException e) {
            log.error("WSO2 API-M Migration Task : Error running the artifact re-indexing script to clear "
                    + "REG_LOB table", e);
        }
        log.info("WSO2 API-M Migration Task : Pre Artifact re-indexing migrator to clear REG_LOG table completed");
    }
}
