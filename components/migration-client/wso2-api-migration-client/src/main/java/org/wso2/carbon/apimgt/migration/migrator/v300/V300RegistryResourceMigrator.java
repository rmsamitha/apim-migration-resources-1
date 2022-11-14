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

package org.wso2.carbon.apimgt.migration.migrator.v300;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.migrator.commonMigrators.RegistryResourceMigrator;
import org.wso2.carbon.apimgt.migration.util.RegistryService;
import org.wso2.carbon.apimgt.migration.util.RegistryServiceImpl;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;

import java.util.List;

public class V300RegistryResourceMigrator extends RegistryResourceMigrator {
    private static final Log log = LogFactory.getLog(V300RegistryResourceMigrator.class);
    private RegistryService registryService;
    List<Tenant> tenants;

    public V300RegistryResourceMigrator(String rxtDir) throws UserStoreException {
        super(rxtDir);
        tenants = loadTenants();
        registryService = new RegistryServiceImpl();
    }

    @Override
    public void migrate() throws APIMigrationException {
        super.migrate();
        updateGenericAPIArtifacts(registryService);
    }

    /**
     * This method is used to update the API artifacts in the registry
     * - to migrate Publisher Access Control feature related data.
     * - to add overview_type property to API artifacts
     *
     * @throws APIMigrationException
     */
    private void updateGenericAPIArtifacts(RegistryService registryService) throws APIMigrationException {
        boolean isError = false;
        log.info("WSO2 API-M Migration Task : Started Updating API artifacts for all tenants");
        for (Tenant tenant : tenants) {
            registryService.startTenantFlow(tenant);
            log.info("WSO2 API-M Migration Task : Updating APIs of tenant " + tenant.getId()
                    + '(' + tenant.getDomain() + ')');
            GenericArtifact[] artifacts = registryService.getGenericAPIArtifacts();
            for (GenericArtifact artifact : artifacts) {
                try {
                    String path = artifact.getPath();
                    if (registryService.isGovernanceRegistryResourceExists(path)) {
                        Object apiResource = registryService.getGovernanceRegistryResource(path);
                        if (apiResource == null) {
                            continue;
                        }
                        registryService.updateGenericAPIArtifactsForAccessControl(path, artifact);
                        registryService.updateGenericAPIArtifact(path, artifact);
                    }
                } catch (GovernanceException e) {
                    log.error("WSO2 API-M Migration Task : Error while accessing API artifact "
                            + "in registry for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')', e);
                    isError = true;
                } catch (RegistryException | UserStoreException e) {
                    log.error("WSO2 API-M Migration Task : Error while updating API artifact "
                            + "in the registry for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')', e);
                    isError = true;
                }
            }
            log.info("WSO2 API-M Migration Task : Completed Updating API artifacts of tenant "
                    + tenant.getId() + '(' + tenant.getDomain() + ')');
            registryService.endTenantFlow();
        }
        if (isError) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error/s occurred during "
                    + "Updating API artifacts of tenants");
        } else {
            log.info("WSO2 API-M Migration Task : Completed Updating API artifacts for all tenants");
        }
    }
}
