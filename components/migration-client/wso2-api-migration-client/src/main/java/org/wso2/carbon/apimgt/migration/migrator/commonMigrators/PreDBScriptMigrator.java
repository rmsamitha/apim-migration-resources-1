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

package org.wso2.carbon.apimgt.migration.migrator.commonMigrators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.migration.util.DBUtil;
import org.wso2.carbon.apimgt.migration.util.RegDBUtil;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

/**
 * Class to run pre-migration DB scripts from version to version
 */
public class PreDBScriptMigrator {
    private String apimgtScriptPath;
    private String regScriptPath;
    private static final Log log = LogFactory.getLog(PreDBScriptMigrator.class);

    public PreDBScriptMigrator(String apimgtScriptPath) {
        this.apimgtScriptPath = apimgtScriptPath;
    }

    public PreDBScriptMigrator(String apimgtScriptPath, String regScriptPath) {
        this.apimgtScriptPath = apimgtScriptPath;
        this.regScriptPath = regScriptPath;
    }

    public void run() {
        try {
            //run pre migration scripts on AM_DB
            if (apimgtScriptPath != null) {
                DBUtil.runSQLScript(apimgtScriptPath, false, APIMgtDBUtil.getConnection());
            }
            //run pre migration scripts on REG_DB
            if (regScriptPath != null) {
                //get all the reg data sources configured in registry.xml except for wso2registry dbConfig
                Map<String, DataSource> dataSourceMap = RegDBUtil.getDataSources();
                for (DataSource dataSource : dataSourceMap.values()) {
                    log.info("WSO2 API-M Migration Task : Running the REG_DB pre migration SQL script for registry " +
                            "database" + dataSource.getConnection());
                    DBUtil.runSQLScript(regScriptPath, false, dataSource.getConnection());
                }
            }
        } catch (SQLException e) {
            log.error("WSO2 API-M Migration Task : Error while running AM_DB pre migration SQL script at " + apimgtScriptPath, e);
        }
    }
}
