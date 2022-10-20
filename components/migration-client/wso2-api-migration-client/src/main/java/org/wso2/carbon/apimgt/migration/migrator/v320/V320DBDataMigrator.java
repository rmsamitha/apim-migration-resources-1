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
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.dto.APIInfoDTO;
import org.wso2.carbon.apimgt.migration.migrator.v320.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.migration.migrator.Migrator;
import org.wso2.carbon.apimgt.migration.migrator.Utility;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifactImpl;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;

import java.util.ArrayList;
import java.util.List;

public class V320DBDataMigrator extends Migrator {

    private static final Log log = LogFactory.getLog(V320DBDataMigrator.class);

    List<Tenant> tenants;
    TenantManager tenantManager;

    ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();

    public V320DBDataMigrator() throws UserStoreException {
        tenants = loadTenants();
    }

    @Override
    public void migrate() throws APIMigrationException {
        log.info("WSO2 API-M Migration Task : Starting API_TYPE update of APIs");
        updateAPITypeInDb();
        log.info("WSO2 API-M Migration Task : Successfully updated API_TYPE of APIs");
    }

    /**
     * Update the API_TYPE in the database when migrating to 3.2.0
     *
     * @throws APIMigrationException APIMigrationException
     */
    private void updateAPITypeInDb() throws APIMigrationException {
        boolean isError = false;
        log.info("WSO2 API-M Migration Task : Started updating API Type in DB for all tenants");
        tenantManager = ServiceHolder.getRealmService().getTenantManager();
        try {
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                log.info("WSO2 API-M Migration Task : Started updating API Type in DB " +
                        "for tenant: " + tenant.getDomain());
                List<APIInfoDTO> apiInfoDTOList = new ArrayList<>();
                try {
                    int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                    APIUtil.loadTenantRegistry(apiTenantId);
                    Utility.startTenantFlow(tenant.getDomain());
                    Registry registry =
                            ServiceHolder.getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                    GenericArtifactManager tenantArtifactManager = APIUtil.getArtifactManager(registry,
                            APIConstants.API_KEY);
                    if (tenantArtifactManager != null) {
                        GenericArtifact[] tenantArtifacts = tenantArtifactManager.getAllGenericArtifacts();
                        for (GenericArtifact artifact : tenantArtifacts) {
                            try {
                                String artifactPath = ((GenericArtifactImpl) artifact).getArtifactPath();
                                if (artifactPath.contains("/apimgt/applicationdata/apis/")) {
                                    continue;
                                }
                                APIInfoDTO apiInfoDTO = new APIInfoDTO();
                                apiInfoDTO.setApiProvider(
                                        APIUtil.replaceEmailDomainBack(artifact.getAttribute("overview_provider")));
                                apiInfoDTO.setApiName(artifact.getAttribute("overview_name"));
                                apiInfoDTO.setApiVersion(artifact.getAttribute("overview_version"));
                                apiInfoDTO.setType(artifact.getAttribute("overview_type"));
                                apiInfoDTOList.add(apiInfoDTO);
                            } catch (GovernanceException e) {
                                log.error("WSO2 API-M Migration Task : Error while " +
                                        "fetching attributes from artifact, artifact path: " +
                                        ((GenericArtifactImpl) artifact).getArtifactPath(), e);
                                isError = true;
                            }
                        }
                        apiMgtDAO.updateApiType(apiInfoDTOList, tenant.getId(), tenant.getDomain());
                    }
                } catch (RegistryException e) {
                    log.error("WSO2 API-M Migration Task : Error while initiation the registry, tenant domain: " +
                            tenant.getDomain(), e);
                    isError = true;
                } catch (UserStoreException e) {
                    log.error("WSO2 API-M Migration Task : Error while retrieving the tenant ID, tenant domain: " +
                            tenant.getDomain(), e);
                    isError = true;
                } catch (APIManagementException e) {
                    log.error("WSO2 API-M Migration Task : Error while retrieving API artifact, tenant domain: " +
                            tenant.getDomain(), e);
                    isError = true;
                } catch (APIMigrationException e) {
                    log.error("WSO2 API-M Migration Task : Error while updating API type, tenant domain: " +
                            tenant.getDomain(), e);
                    isError = true;
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
            isError = true;
        }
        if (isError) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error/s occurred during " +
                    "updating API Type in DB for all tenants");
        } else {
            log.info("WSO2 API-M Migration Task : Completed updating API Type in DB for all tenants");
        }
    }
}
