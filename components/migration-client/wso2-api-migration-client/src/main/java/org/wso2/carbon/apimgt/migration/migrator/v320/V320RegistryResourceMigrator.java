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

package org.wso2.carbon.apimgt.migration.migrator.v320;

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

public class V320RegistryResourceMigrator extends RegistryResourceMigrator {
    private static final Log log = LogFactory.getLog(V320RegistryResourceMigrator.class);
    private RegistryService registryService;
    List<Tenant> tenants;

    public V320RegistryResourceMigrator(String rxtDir) throws UserStoreException {
        super(rxtDir);
        tenants = loadTenants();
        registryService = new RegistryServiceImpl();
    }
    public void migrate() throws APIMigrationException {
        super.migrate();
        updateEnableStoreInRxt();
        updateAPIPropertyVisibility();
    }

    /**
     * This method updates the 'enableStore' rxt field in migrated APIs as it has been mandated in devportal api's
     * listing
     */
    private void updateEnableStoreInRxt() {
        for (Tenant tenant : tenants) {
            try {
                registryService.startTenantFlow(tenant);
                log.info("WSO2 API-M Migration Task : Updating enableStore property of APIs for tenant "
                        + tenant.getId() + '(' + tenant.getDomain() + ") - All existing APIs will be marked as "
                        + "'enabledStore=true'");
                GenericArtifact[] artifacts = registryService.getGenericAPIArtifacts();
                for (GenericArtifact artifact : artifacts) {
                    String path = artifact.getPath();
                    if (registryService.isGovernanceRegistryResourceExists(path)) {
                        Object apiResource = registryService.getGovernanceRegistryResource(path);
                        if (apiResource == null) {
                            continue;
                        }
                        registryService.updateEnableStoreInRxt(path, artifact);
                    }
                }
                log.info("WSO2 API-M Migration Task : Completed updating enableStore property of API artifacts "
                        + "for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
            } catch (GovernanceException e) {
                log.error("WSO2 API-M Migration Task : Error while accessing API artifact in registry for tenant "
                        + tenant.getId() + '(' + tenant.getDomain() + ')', e);
            } catch (RegistryException | UserStoreException e) {
                log.error("WSO2 API-M Migration Task : Error while updating API artifact in the registry for tenant " + tenant.getId() + '(' +
                        tenant.getDomain() + ')', e);
            } finally {
                registryService.endTenantFlow();
            }
        }
    }

    /**
     * This method updates the existing API properties so that they will be visible in the store by default
     * after migration
     */
    private void updateAPIPropertyVisibility() throws APIMigrationException {
        boolean isError = false;
        for (Tenant tenant : tenants) {
            registryService.startTenantFlow(tenant);
            log.info("WSO2 API-M Migration Task : Updating API property visibility for tenant " +
                    tenant.getId() + '(' + tenant.getDomain() + ") - All existing properties will be marked as "
                    + "'visible on developer portal'");
            GenericArtifact[] artifacts = registryService.getGenericAPIArtifacts();
            for (GenericArtifact artifact : artifacts) {
                try {
                    String path = artifact.getPath();
                    if (registryService.isGovernanceRegistryResourceExists(path)) {
                        Object apiResource = registryService.getGovernanceRegistryResource(path);
                        if (apiResource == null) {
                            continue;
                        }
                        registryService.updateAPIPropertyVisibility(path);
                    }
                } catch (GovernanceException e) {
                    log.error("WSO2 API-M Migration Task : Error while accessing API artifact " +
                            "in registry for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')', e);
                    isError = true;
                } catch (RegistryException | UserStoreException e) {
                    log.error("WSO2 API-M Migration Task : Error while updating API artifact " +
                            "in the registry for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')', e);
                    isError = true;
                }
            }
            log.info("WSO2 API-M Migration Task : Completed updating API property visibilities for tenant  "
                        + tenant.getId() + '(' + tenant.getDomain() + ')');
            registryService.endTenantFlow();
        }
        if (isError) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error/s occurred during "
                    + "updating API property visibilities for tenants");
        } else {
            log.info("WSO2 API-M Migration Task : Completed updating API property visibilities for tenants");
        }
    }
}
