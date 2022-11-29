/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.apimgt.migration.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.user.api.RealmConfiguration;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.config.RealmConfigXMLProcessor;
import org.wso2.carbon.user.core.util.DatabaseUtil;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public final class UserDBUtil {

    private static final Log log = LogFactory.getLog(UserDBUtil.class);
    private static volatile DataSource dataSource = null;

    /**
     * Initializes the data source
     *
     * @throws APIMigrationException if an error occurs while building realm configuration from file
     */
    public static void initialize() throws APIMigrationException {
        RealmConfiguration realmConfig;
        try {
            realmConfig = new RealmConfigXMLProcessor().buildRealmConfigurationFromFile();
            dataSource = DatabaseUtil.getRealmDataSource(realmConfig);
        } catch (UserStoreException e) {
            throw new APIMigrationException("Error while building realm configuration from file", e);
        }
    }

    /**
     * Utility method to get a new database connection
     *
     * @return Connection
     * @throws APIMigrationException if failed to get Connection
     */
    public static Connection getConnection() throws APIMigrationException {
        try {
            if (dataSource != null) {
                return dataSource.getConnection();
            }
        } catch (SQLException e) {
            throw new APIMigrationException("Failed to get Connection.", e);
        }
        throw new APIMigrationException("Data source is not configured properly.");
    }
}
