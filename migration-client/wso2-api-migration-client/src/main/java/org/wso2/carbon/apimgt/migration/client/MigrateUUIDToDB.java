/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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


package org.wso2.carbon.apimgt.migration.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.dao.APIMgtDAO;
import org.wso2.carbon.apimgt.migration.dto.APIInfoDTO;
import org.wso2.carbon.apimgt.migration.util.RegistryService;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.util.ArrayList;
import java.util.List;

public class MigrateUUIDToDB extends MigrationClientBase{

    protected Registry registry;
    protected TenantManager tenantManager;
    private static final Log log = LogFactory.getLog(MigrateUUIDToDB.class);
    APIMgtDAO apiMgtDAO = APIMgtDAO.getInstance();
    public MigrateUUIDToDB(String tenantArguments, String blackListTenantArguments, String tenantRange,
                           TenantManager tenantManager) throws UserStoreException, APIManagementException {
        super(tenantArguments, blackListTenantArguments, tenantRange, tenantManager);
        this.tenantManager = tenantManager;
    }

    /**
     * Get the List of APIs and pass it to DAO method to update the uuid
     * @throws APIMigrationException
     */
    public void moveUUIDToDBFromRegistry() throws APIMigrationException {
        boolean isError = false;
        log.info("WSO2 API-M Migration Task : Adding API UUID and STATUS to AM_API table for all tenants");
        List<APIInfoDTO> apiInfoDTOList = new ArrayList<>();
        try {
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                log.info("WSO2 API-M Migration Task : Adding API UUID and STATUS to AM_API table for tenant: "
                        + tenant.getId() + '(' + tenant.getDomain() + ')');
                try {
                    int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                    APIUtil.loadTenantRegistry(apiTenantId);
                    startTenantFlow(tenant.getDomain());
                    Registry registry =
                            ServiceReferenceHolder.getInstance().getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                    GenericArtifactManager tenantArtifactManager = APIUtil.getArtifactManager(registry,
                            APIConstants.API_KEY);
                    if (tenantArtifactManager != null) {
                        GenericArtifact[] tenantArtifacts = tenantArtifactManager.getAllGenericArtifacts();
                        if (tenantArtifacts == null || tenantArtifacts.length == 0) {
                            continue;
                        }
                        for (GenericArtifact artifact : tenantArtifacts) {
                            try {
                                API api = APIUtil.getAPI(artifact);
                                if (api != null) {
                                    APIInfoDTO apiInfoDTO = new APIInfoDTO();
                                    apiInfoDTO.setUuid(api.getUUID());
                                    apiInfoDTO.setApiProvider(APIUtil.replaceEmailDomainBack(api.getId().getProviderName()));
                                    apiInfoDTO.setApiName(api.getId().getApiName());
                                    apiInfoDTO.setApiVersion(api.getId().getVersion());
                                    apiInfoDTO.setStatus(api.getStatus());
                                    apiInfoDTOList.add(apiInfoDTO);
                                }
                            } catch (APIManagementException e) {
                                log.error("WSO2 API-M Migration Task : Error while getting API from API Util: ", e);
                                isError = true;
                            }
                        }
                    } else {
                        log.info("WSO2 API-M Migration Task : Adding API UUID and STATUS to AM_API table, skipped for tenant: "
                                + tenant.getId() + "(" + tenant.getDomain() + ") as no api artifacts found in registry");
                    }
                } catch (RegistryException e) {
                    log.error("WSO2 API-M Migration Task : Error while initiation the registry, tenant domain: "
                            + tenant.getDomain(), e);
                    isError = true;
                } catch (UserStoreException e) {
                    log.error("WSO2 API-M Migration Task : Error while retrieving the tenant ID, tenant domain: "
                            + tenant.getDomain(), e);
                    isError = true;
                } catch (APIManagementException e) {
                    log.error("WSO2 API-M Migration Task : Error while retrieving API artifact from the registry, "
                            + "tenant domain: " + tenant.getDomain(), e);
                    isError = true;
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
            apiMgtDAO.updateUUIDAndStatus(apiInfoDTOList);
            log.info("WSO2 API-M Migration Task : Added API UUID and STATUS to AM_API table for all tenants");
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
            isError = true;
        }
        if (isError) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error/s occurred while "
                    + "adding API UUID and STATUS to AM_API table for all tenants");
        } else {
            log.info("WSO2 API-M Migration Task : Added API UUID and STATUS to AM_API table for all tenants");
        }
    }
    protected void startTenantFlow(String tenantDomain) {
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
    }
}
