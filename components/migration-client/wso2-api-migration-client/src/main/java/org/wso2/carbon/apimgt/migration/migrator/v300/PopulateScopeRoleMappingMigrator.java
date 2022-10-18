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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONObject;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.migrator.Migrator;
import org.wso2.carbon.apimgt.migration.migrator.Utility;
import org.wso2.carbon.apimgt.migration.dao.SharedDAO;
import org.wso2.carbon.apimgt.migration.dto.UserRoleFromPermissionDTO;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.RegistryServiceImpl;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;

import java.io.IOException;
import java.util.List;

public class PopulateScopeRoleMappingMigrator extends Migrator {
    private static final Log log = LogFactory.getLog(PopulateScopeRoleMappingMigrator.class);
    List<Tenant> tenants;
    RegistryServiceImpl registryService;

    public PopulateScopeRoleMappingMigrator() throws UserStoreException {
        tenants = loadTenants();
        registryService = new RegistryServiceImpl();
    }

    @Override
    public void migrate() throws APIMigrationException {
      populateRoleMappingWithUserRoles();
    }

    /**
     * This method is used to update the scopes of the user roles which will be retrieved based on the
     * permissions assigned.
     */
    private void populateRoleMappingWithUserRoles() {
        log.info("WSO2 API-M Migration Task : Updating User Roles based on Permissions started.");
        for (Tenant tenant : tenants) {
            try {
                registryService.startTenantFlow(tenant);
                Utility.loadAndSyncTenantConf(tenant.getId());
                log.info("WSO2 API-M Migration Task : Updating user roles for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');

                // Retrieve user roles which has create permission
                List<UserRoleFromPermissionDTO> userRolesListWithCreatePermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermission(Constants.API_CREATE, tenant.getId());

                // Retrieve user roles which has publish permission
                List<UserRoleFromPermissionDTO> userRolesListWithPublishPermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermission(Constants.API_PUBLISH, tenant.getId());

                // Retrieve user roles which has subscribe permission
                List<UserRoleFromPermissionDTO> userRolesListWithSubscribePermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermission(Constants.API_SUBSCRIBE, tenant.getId());

                // Retrieve user roles which has manage API permission
                List<UserRoleFromPermissionDTO> userRolesListWithManageAPIPermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermission(Constants.API_MANAGE, tenant.getId());
                userRolesListWithCreatePermission.addAll(userRolesListWithManageAPIPermission);
                userRolesListWithPublishPermission.addAll(userRolesListWithManageAPIPermission);
                userRolesListWithSubscribePermission.addAll(userRolesListWithManageAPIPermission);

                // Retrieve user roles which has admin permissions
                List<UserRoleFromPermissionDTO> userRolesListWithAdminPermission = SharedDAO.getInstance()
                        .getRoleNamesMatchingPermissions(Utility.makePermissionsStringByEscapingSlash(
                                Constants.APIM_ADMIN, "/permission"), tenant.getId());

                // Retrieve the tenant-conf.json of the corresponding tenant
                JSONObject tenantConf = Utility.getTenantConfigFromRegistry(tenant.getId());

                // Extract the RoleMappings object (This will be null if this does not exist at the moment)
                JSONObject roleMappings = (JSONObject) tenantConf.get(APIConstants.REST_API_ROLE_MAPPINGS_CONFIG);
                if (roleMappings == null) {
                    // Create RoleMappings field in tenant-conf.json and retrieve the object
                    tenantConf.put(APIConstants.REST_API_ROLE_MAPPINGS_CONFIG, new JSONObject());
                    roleMappings = (JSONObject) tenantConf.get(APIConstants.REST_API_ROLE_MAPPINGS_CONFIG);
                }

                Utility.createOrUpdateRoleMappingsField(roleMappings, userRolesListWithCreatePermission,
                        userRolesListWithPublishPermission, userRolesListWithSubscribePermission,
                        userRolesListWithAdminPermission);

                ObjectMapper mapper = new ObjectMapper();
                String formattedTenantConf = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenantConf);

                updateTenantConf(formattedTenantConf, tenant.getId());


                log.info("WSO2 API-M Migration Task : Updated tenant-conf.json for tenant " + tenant.getId() +
                        '(' + tenant.getDomain() + ')' + "\n" + formattedTenantConf);

                log.info("WSO2 API-M Migration Task : End updating user roles for tenant " + tenant.getId() +
                        '(' + tenant.getDomain() + ')');
            } catch (APIMigrationException e) {
                log.error("WSO2 API-M Migration Task : Error while retrieving role names based on "
                        + "existing permissions. ", e);
            } catch (JsonProcessingException e) {
                log.error("WSO2 API-M Migration Task :Error while formatting tenant-conf.json of tenant "
                        + tenant.getId());
            } catch (IOException e) {
                log.error("WSO2 API-M Migration Task : Error occurred while writing tenant-conf.json value to "
                        + "string." + tenant.getId(), e);
            } finally {
                registryService.endTenantFlow();
            }
        }
        log.info("WSO2 API-M Migration Task : Updating User Roles done for all the tenants.");
    }

    public static void updateTenantConf(String tenantConfString, int tenantId) throws APIMigrationException {

        org.wso2.carbon.registry.core.service.RegistryService registryService = ServiceHolder.getRegistryService();
        try {
            UserRegistry registry = registryService.getConfigSystemRegistry(tenantId);
            updateTenantConf(registry, tenantConfString.getBytes());
        } catch (RegistryException e) {
            throw new APIMigrationException("Error while saving tenant conf to the registry of tenant "
                    + tenantId, e);
        }
    }

    private static void updateTenantConf(UserRegistry registry, byte[] data) throws RegistryException {

        Resource resource = registry.newResource();
        resource.setMediaType(APIConstants.API_TENANT_CONF_MEDIA_TYPE);
        resource.setContent(data);
        registry.put(APIConstants.API_TENANT_CONF_LOCATION, resource);
    }
}
