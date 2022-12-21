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


package org.wso2.carbon.apimgt.migration.migrator;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Migrator {
    private static final Log log = LogFactory.getLog(Migrator.class);
    private String tenantRange;
    private TenantManager tenantManager;
    private List<Tenant> tenantsArray;
    String preMigrationStep = System.getProperty(Constants.PRE_MIGRATION_STEP);
    String tenantArguments = System.getProperty(Constants.ARG_MIGRATE_TENANTS);
    String tenantRangeArgs = System.getProperty(Constants.ARG_MIGRATE_TENANTS_RANGE);
    String blackListTenantArguments = System.getProperty(Constants.ARG_MIGRATE_BLACKLIST_TENANTS);

    /**
     * Migrator specific implementation.
     */
    public abstract void migrate() throws APIMigrationException;

    public List<Tenant> loadTenants() throws UserStoreException {
        TenantManager tenantManager = ServiceHolder.getRealmService().getTenantManager();
        this.tenantManager = tenantManager;
        this.tenantRange = tenantRangeArgs;
        if (tenantArguments != null) {  // Tenant arguments have been provided so need to load specific ones
            tenantArguments = tenantArguments.replaceAll("\\s", ""); // Remove spaces and tabs

            tenantsArray = new ArrayList<>();

            Utility.buildTenantList(tenantManager, tenantsArray, tenantArguments);
            this.tenantArguments = tenantArguments;
        } else if (blackListTenantArguments != null) {
            blackListTenantArguments = blackListTenantArguments.replaceAll("\\s", ""); // Remove spaces and tabs

            List<Tenant> blackListTenants = new ArrayList<>();
            Utility.buildTenantList(tenantManager, blackListTenants, blackListTenantArguments);
            this.blackListTenantArguments = blackListTenantArguments;

            List<Tenant> allTenants = new ArrayList<>(Arrays.asList(tenantManager.getAllTenants()));
            Tenant superTenant = new Tenant();
            superTenant.setDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            superTenant.setId(MultitenantConstants.SUPER_TENANT_ID);
            allTenants.add(superTenant);

            tenantsArray = new ArrayList<>();

            for (Tenant tenant : allTenants) {
                boolean isBlackListed = false;
                for (Tenant blackListTenant : blackListTenants) {
                    if (blackListTenant.getId() == tenant.getId()) {
                        isBlackListed = true;
                        break;
                    }
                }

                if (!isBlackListed) {
                    tenantsArray.add(tenant);
                }
            }
        } else if (tenantRange != null) {
            tenantsArray = new ArrayList<Tenant>();
            int l, u;
            try {
                l = Integer.parseInt(tenantRange.split("-")[0].trim());
                u = Integer.parseInt(tenantRange.split("-")[1].trim());
            } catch (Exception e) {
                throw new UserStoreException("TenantRange argument is not properly set. use format 1-12", e);
            }
            log.debug("no of Tenants " + tenantManager.getAllTenants().length);
            int lastIndex = tenantManager.getAllTenants().length - 1;
            log.debug("last Tenant id " + tenantManager.getAllTenants()[lastIndex].getId());
            for (Tenant t : tenantManager.getAllTenants()) {
                if (t.getId() > l && t.getId() < u) {
                    log.debug("using tenants " + t.getDomain() + "(" + t.getId() + ")");
                    tenantsArray.add(t);
                }
            }
        } else {  // Load all tenants
            tenantsArray = new ArrayList<>(Arrays.asList(tenantManager.getAllTenants()));
            Tenant superTenant = new Tenant();
            superTenant.setDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            superTenant.setId(MultitenantConstants.SUPER_TENANT_ID);
            tenantsArray.add(superTenant);
        }

        log.info("Setting tenant admin names....");

        for (int i = 0; i < tenantsArray.size(); ++i) {
            Tenant tenant = tenantsArray.get(i);
            if (tenant.getId() == MultitenantConstants.SUPER_TENANT_ID) {
                String tenantDomain = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                        getSuperTenantDomain();
                String adminName = getTenantAdminUserName(tenantDomain);
                log.info("Setting super tenant: " + tenantDomain + " and admin name: " + adminName
                        + " to base tenant array.");
                tenant.setAdminName(adminName);
            } else {
                log.info("Setting tenant: " + tenant.getId() + " to base tenant array.");
                tenantsArray.set(i, tenantManager.getTenant(tenant.getId()));
            }
        }
        return tenantsArray;
    }

    /**
     * Get tenant admin user name.
     *
     * @param tenantDomain tenant domain
     * @return admin user name
     * @throws UserStoreException if an error occurs
     */
    private String getTenantAdminUserName(String tenantDomain) throws UserStoreException {

        try {
            int tenantId = ServiceHolder.getRealmService().getTenantManager().
                    getTenantId(tenantDomain);
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain);
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId);
            String adminUserName = ServiceHolder.getRealmService().getTenantUserRealm(tenantId)
                    .getRealmConfiguration().getAdminUserName();
            if (!tenantDomain.contentEquals(org.wso2.carbon.base.MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)) {
                return adminUserName.concat("@").concat(tenantDomain);
            }
            return adminUserName;
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }
}
