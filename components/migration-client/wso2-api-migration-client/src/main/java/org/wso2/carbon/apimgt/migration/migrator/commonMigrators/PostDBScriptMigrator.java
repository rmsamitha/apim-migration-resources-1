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
import org.wso2.carbon.apimgt.migration.util.AMDBUtil;

import java.sql.SQLException;

/**
 * Class to run post-migration DB scripts from version to version
 */
public class PostDBScriptMigrator {
    private String scriptPath;
    public PostDBScriptMigrator(String scriptPath) {
        this.scriptPath = scriptPath;
    }
    private static final Log log = LogFactory.getLog(PostDBScriptMigrator.class);

    public void run() {
        try {
            AMDBUtil.runSQLScript(scriptPath, false);
        } catch (SQLException e) {
            log.error("WSO2 API-M Migration Task : Error while running AM_DB post migration SQL scripts ", e);
        }
    }
}
