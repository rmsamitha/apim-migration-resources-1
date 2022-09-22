/*
 *   Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.apimgt.migration.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerFactory;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.wsdl.util.SOAPOperationBindingUtils;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.dao.APIMgtDAO;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.governance.lcm.util.CommonUtil;
import org.wso2.carbon.registry.core.ActionConstants;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.realm.RegistryAuthorizationManager;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;

import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class RegistryServiceImpl implements RegistryService {
    private static final Log log = LogFactory.getLog(RegistryServiceImpl.class);
    private Tenant tenant = null;
    private APIProvider apiProvider = null;

    @Override
    public void startTenantFlow(Tenant tenant) {
        if (this.tenant != null) {
            log.error("WSO2 API-M Migration Task : Start tenant flow called without ending previous tenant flow");
            throw new IllegalStateException("Previous tenant flow has not been ended, " +
                                                "'RegistryService.endTenantFlow()' needs to be called");
        } else {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenant.getDomain(), true);
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenant.getId(), true);
            try {
                APIUtil.loadTenantRegistry(tenant.getId());
            } catch (RegistryException e) {
                log.error("WSO2 API-M Migration Task : Could not load tenant registry for tenant " + tenant.getId() + '('
                        + tenant.getDomain() + ')', e);
            }
            this.tenant = tenant;
        }
    }

    @Override
    public void endTenantFlow() {
        if (this.tenant == null) {
            log.error("WSO2 API-M Migration Task : End tenant flow called even though tenant flow has already been ended or was not started");
            throw new IllegalStateException("Previous tenant flow has already been ended, " +
                    "unnecessary additional RegistryService.endTenantFlow()' call has been detected");
        } else {
            PrivilegedCarbonContext.endTenantFlow();
            this.tenant = null;
            this.apiProvider = null;
        }
    }

    @Override
    public void rollbackGovernanceRegistryTransaction() throws UserStoreException, RegistryException {
        getGovernanceRegistry().rollbackTransaction();
    }

    @Override
    public void rollbackConfigRegistryTransaction() throws UserStoreException, RegistryException {
        getConfigRegistry().rollbackTransaction();
    }

    @Override
    public void addDefaultLifecycles() throws RegistryException, UserStoreException, FileNotFoundException, XMLStreamException {
        CommonUtil.addDefaultLifecyclesIfNotAvailable(getConfigRegistry(), CommonUtil.getRootSystemRegistry(tenant.getId()));
    }

    @Override
    public GenericArtifact[] getGenericAPIArtifacts() {
        log.debug("Calling getGenericAPIArtifacts");
        GenericArtifact[] artifacts = {};

        try {
            Registry registry = getGovernanceRegistry();
            GenericArtifactManager artifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);

            if (artifactManager != null) {
                artifacts = artifactManager.getAllGenericArtifacts();

                log.debug("Total number of api artifacts : " + artifacts.length);
            } else {
                log.debug("No api artifacts found in registry for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
            }

        } catch (RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error occurred when getting GenericArtifacts from registry", e);
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error occurred while reading tenant information of tenant " + tenant.getId() + '(' + tenant.getDomain() + ')', e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Failed to initialize GenericArtifactManager", e);
        }

        return artifacts;
    }

    @Override
    public void updateGenericAPIArtifacts(GenericArtifact[] artifacts) {
        log.debug("Calling updateGenericAPIArtifacts");

        try {
            Registry registry = getGovernanceRegistry();
            GenericArtifactManager artifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);

            for (GenericArtifact artifact : artifacts) {
                try {
                    artifactManager.updateGenericArtifact(artifact);
                } catch (GovernanceException e) {
                    // This is to avoid the loop from exiting if one artifact fails.
                    log.error("WSO2 API-M Migration Task : Unable to update governance artifact", e);
                }
            }
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error occurred while reading tenant information of tenant " + tenant.getId() + '(' + tenant.getDomain() + ')', e);
        } catch (RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error occurred when updating GenericArtifacts in registry", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Failed to initialize GenericArtifactManager", e);
        }
    }

    @Override
    public API getAPI(GenericArtifact artifact) {
        log.debug("Calling getAPI");
        API api = null;

        try {
            api = APIUtil.getAPI(artifact);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error when getting api artifact " + artifact.getId() + " from registry", e);
        }
        return api;
    }

    @Override
    public String getGenericArtifactPath(GenericArtifact artifact) throws UserStoreException, RegistryException {
        return GovernanceUtils.getArtifactPath(getGovernanceRegistry(), artifact.getId());
    }

    @Override
    public boolean isConfigRegistryResourceExists(String registryLocation) throws UserStoreException, RegistryException {
        return getConfigRegistry().resourceExists(registryLocation);
    }

    @Override
    public boolean isGovernanceRegistryResourceExists(String registryLocation) throws UserStoreException, RegistryException {
        return getGovernanceRegistry().resourceExists(registryLocation);
    }

    @Override
    public Object getConfigRegistryResource(final String registryLocation) throws UserStoreException, RegistryException {
        Object content = null;

        Registry registry = getConfigRegistry();

        if (registry.resourceExists(registryLocation)) {
            Resource resource = registry.get(registryLocation);
            content = resource.getContent();
        }

        return content;
    }

    @Override
    public Object getGovernanceRegistryResource(final String registryLocation) throws UserStoreException, RegistryException {
        Object content = null;

        Registry registry = getGovernanceRegistry();

        if (registry.resourceExists(registryLocation)) {
            Resource resource = registry.get(registryLocation);
            content = resource.getContent();
        }

        return content;
    }

    @Override
    public void addConfigRegistryResource(final String registryLocation, final String content,
                                          final String mediaType) throws UserStoreException, RegistryException {
        Registry registry = getConfigRegistry();

        Resource resource = registry.newResource();
        resource.setContent(content);
        resource.setMediaType(mediaType);
        registry.put(registryLocation, resource);
    }

    @Override
    public void addGovernanceRegistryResource(final String registryLocation, final String content,
                                              final String mediaType) throws UserStoreException, RegistryException {
        Registry registry = getGovernanceRegistry();

        Resource resource = registry.newResource();
        resource.setContent(content);
        resource.setMediaType(mediaType);
        registry.put(registryLocation, resource);
    }

    @Override
    public void updateConfigRegistryResource(final String registryLocation, final String content)
                                                                        throws UserStoreException, RegistryException {
        Registry registry = getConfigRegistry();

        Resource resource = registry.get(registryLocation);
        resource.setContent(content);
        registry.put(registryLocation, resource);
    }


    @Override
    public void updateGovernanceRegistryResource(final String registryLocation, final String content)
                                                                        throws UserStoreException, RegistryException {
        Registry registry = getGovernanceRegistry();

        Resource resource = registry.get(registryLocation);
        resource.setContent(content);
        registry.put(registryLocation, resource);
    }

    @Override
    public void setGovernanceRegistryResourcePermissions(String userName, String visibility, String[] roles,
                                                                String resourcePath) throws APIManagementException {
        initAPIProvider();
        APIUtil.setResourcePermissions(userName, visibility, roles, resourcePath);
    }

    private void initAPIProvider() throws APIManagementException {
        if (apiProvider == null) {
            apiProvider = APIManagerFactory.getInstance().getAPIProvider(tenant.getAdminName());
        }
    }


    private Registry getConfigRegistry() throws UserStoreException, RegistryException {
        if (tenant == null) {
            throw new IllegalStateException("The tenant flow has not been started, " +
                        "'RegistryService.startTenantFlow(Tenant tenant)' needs to be called");
        }

        String adminName = ServiceHolder.getRealmService().getTenantUserRealm(tenant.getId()).
                getRealmConfiguration().getAdminUserName();
        log.debug("Tenant admin username : " + adminName);
        ServiceHolder.getTenantRegLoader().loadTenantRegistry(tenant.getId());
        return ServiceHolder.getRegistryService().getConfigUserRegistry(adminName, tenant.getId());
    }


    public Registry getGovernanceRegistry() throws UserStoreException, RegistryException {
        if (tenant == null) {
            throw new IllegalStateException("The tenant flow has not been started, " +
                    "'RegistryService.startTenantFlow(Tenant tenant)' needs to be called");
        }

        String adminName = ServiceHolder.getRealmService().getTenantUserRealm(tenant.getId()).
                getRealmConfiguration().getAdminUserName();
        log.debug("Tenant admin username : " + adminName);
        ServiceHolder.getTenantRegLoader().loadTenantRegistry(tenant.getId());
        return ServiceHolder.getRegistryService().getGovernanceUserRegistry(adminName, tenant.getId());
    }

    /* 
     * Update the RXT file in the registry 
     * 
     */
    @Override
    public void updateRXTResource(String rxtName, final String rxtPayload) throws UserStoreException, RegistryException {
        if (tenant == null) {
            throw new IllegalStateException("The tenant flow has not been started, "
                    + "'RegistryService.startTenantFlow(Tenant tenant)' needs to be called");
        }
        ServiceHolder.getTenantRegLoader().loadTenantRegistry(tenant.getId());

        Registry registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(tenant.getId());

        //Update RXT resource
        String resourcePath = Constants.RXT_REG_PATH + RegistryConstants.PATH_SEPARATOR + rxtName;
        
        // This is "registry" is a governance registry instance, therefore
        // calculate the relative path to governance.
        String govRelativePath = RegistryUtils.getRelativePathToOriginal(resourcePath,
                APIUtil.getMountedPath(RegistryContext.getBaseInstance(),
                        RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH));
        // calculate resource path
        RegistryAuthorizationManager authorizationManager = new RegistryAuthorizationManager(
                ServiceReferenceHolder.getUserRealm());
        resourcePath = authorizationManager.computePathOnMount(resourcePath);
        org.wso2.carbon.user.api.AuthorizationManager authManager = ServiceReferenceHolder.getInstance()
                .getRealmService()
                .getTenantUserRealm(tenant.getId())
                .getAuthorizationManager();

        if (registry.resourceExists(govRelativePath)) {
            Resource resource = registry.get(govRelativePath);
            resource.setContent(rxtPayload.getBytes(Charset.defaultCharset()));
            resource.setMediaType(APIConstants.RXT_MEDIA_TYPE);
            registry.put(govRelativePath, resource);
            log.info("Updating the governance registry: " + govRelativePath + " for tenant id: " + tenant.getId());
            authManager.authorizeRole(APIConstants.ANONYMOUS_ROLE, resourcePath, ActionConstants.GET);
            log.info("Authorizing role: " + APIConstants.ANONYMOUS_ROLE + " on the registry resource: "
                    + resourcePath + " for tenant id: " + tenant.getId());
        }

        //Update RXT UI Configuration
        Registry configRegistry = ServiceHolder.getRegistryService().getConfigSystemRegistry(tenant.getId());
        String rxtUIConfigPath = Constants.GOVERNANCE_ARTIFACT_CONFIGURATION_PATH + APIConstants.API_KEY;
        if (configRegistry.resourceExists(rxtUIConfigPath)) {
            Resource rxtUIResource = configRegistry.get(rxtUIConfigPath);
            rxtUIResource.setContent(ResourceUtil.getArtifactUIContentFromConfig(rxtPayload));
            configRegistry.put(rxtUIConfigPath, rxtUIResource);
            log.info("Updating the rxt ui resource in config registry: " + rxtUIConfigPath + " for tenant id: "
                    + tenant.getId());
        }
    }

    /**
     * Update API artifacts for Publisher Access Control feature
     * @param resourcePath resource path
     * @param artifact generic artifact
     */
    @Override
    public void updateGenericAPIArtifactsForAccessControl(String resourcePath, GenericArtifact artifact) {
        try {
            Registry registry = getGovernanceRegistry();
            Resource resource = registry.get(resourcePath);
            boolean isResourceUpdated = false;
            if (resource != null) {
                String publisherAccessControl = resource.getProperty(Constants.PUBLISHER_ROLES);
                if (publisherAccessControl == null || publisherAccessControl.trim().isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug("API at " + resourcePath + "did not have property : " + Constants.PUBLISHER_ROLES
                                + ", hence adding the null value for that API resource.");
                    }
                    resource.setProperty(Constants.PUBLISHER_ROLES, Constants.NULL_USER_ROLE_LIST);
                    resource.setProperty(Constants.ACCESS_CONTROL, Constants.NO_ACCESS_CONTROL);
                    isResourceUpdated = true;
                }
                String storeViewRoles = resource.getProperty(Constants.STORE_VIEW_ROLES);
                String storeVisibility = artifact.getAttribute(Constants.API_OVERVIEW_VISIBILITY);
                String storeVisibleRoles = artifact.getAttribute(Constants.API_OVERVIEW_VISIBLE_ROLES);
                if (storeViewRoles == null) {
                    if (Constants.PUBLIC_STORE_VISIBILITY.equals(storeVisibility) || publisherAccessControl == null ||
                            publisherAccessControl.trim().isEmpty() || publisherAccessControl.equals(Constants.NULL_USER_ROLE_LIST)) {
                        if (log.isDebugEnabled()) {
                            log.debug("API at " + resourcePath + "has the public visibility, but  : "
                                    + Constants.STORE_VIEW_ROLES + " property is not set to "
                                    + Constants.NULL_USER_ROLE_LIST + ". Hence setting the correct value.");
                        }
                        resource.setProperty(Constants.STORE_VIEW_ROLES, Constants.NULL_USER_ROLE_LIST);
                        isResourceUpdated = true;
                    } else {
                        StringBuilder combinedRoles = new StringBuilder(publisherAccessControl);
                        String[] roles = storeVisibleRoles.split(",");
                        for (String role : roles) {
                            combinedRoles.append(",").append(role.trim().toLowerCase());
                        }
                        resource.setProperty(Constants.STORE_VIEW_ROLES, String.valueOf(combinedRoles));
                        isResourceUpdated = true;
                    }
                }
                if (isResourceUpdated) {
                    registry.put(resourcePath, resource);
                }
            }
        } catch (RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error occurred when updating GenericArtifacts in registry for the Publisher Access Control " +
                    "feature", e);
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error occurred while reading tenant information of tenant " + tenant.getId() + '('
                    + tenant.getDomain() + ')', e);
        }
    }

    @Override
    public void updateGenericAPIArtifact(String resourcePath, GenericArtifact artifact) {
        try {
            Registry registry = getGovernanceRegistry();
            GenericArtifactManager artifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);
            boolean isResourceUpdated = false;
            String overview_type = artifact.getAttribute(Constants.API_OVERVIEW_TYPE);
            String overview_wsdl = artifact.getAttribute(Constants.API_OVERVIEW_WSDL);
            if (!StringUtils.isEmpty(overview_wsdl)) {
                if (SOAPOperationBindingUtils.isSOAPToRESTApi(artifact.getAttribute(Constants.API_OVERVIEW_NAME),
                        artifact.getAttribute(Constants.API_OVERVIEW_VERSION),
                        artifact.getAttribute(Constants.API_OVERVIEW_PROVIDER))) {
                    if (log.isDebugEnabled()) {
                        log.debug("API at " + resourcePath + " is a SOAPTOREST API, hence adding the overview_type" +
                                " as SOAPTOREST for that API resource.");
                    }
                    overview_type = Constants.API_TYPE_SOAPTOREST;
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("API at " + resourcePath + " is a SOAP API, hence adding the overview_type"
                                + " as SOAP for that API resource.");
                    }
                    overview_type = Constants.API_TYPE_SOAP;
                }
                artifact.setAttribute(Constants.API_OVERVIEW_TYPE, overview_type);
                isResourceUpdated = true;
            }
            if (overview_type == null || overview_type.trim().isEmpty() || "NULL".equalsIgnoreCase(overview_type)) {
                if (log.isDebugEnabled()) {
                    log.debug("API at " + resourcePath + " did not have property : " + Constants.API_OVERVIEW_TYPE
                            + ", hence adding the default value - HTTP for that API resource.");
                }
                artifact.setAttribute(Constants.API_OVERVIEW_TYPE, Constants.API_TYPE_HTTP);
                isResourceUpdated = true;
            }
            if (isResourceUpdated) {
                artifactManager.updateGenericArtifact(artifact);
            }
        } catch (UserStoreException | RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error occurred when updating GenericArtifacts in registry", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error occurred when getting artifact manager", e);
        }
    }

    /**
     * This method updates the 'enableStore' rxt field in migrated APIs as it has been mandated in devportal api's listing
     * @param resourcePath
     * @param artifact
     */
    @Override
    public void updateEnableStoreInRxt(String resourcePath, GenericArtifact artifact) {
        try {
            Registry registry = getGovernanceRegistry();
            GenericArtifactManager artifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);
            boolean isResourceUpdated = false;
            boolean enableStore = Boolean.parseBoolean(artifact.getAttribute(Constants.API_OVERVIEW_ENABLE_STORE));
            if (enableStore == false) {
                if (log.isDebugEnabled()) {
                    log.debug("Setting " + Constants.API_OVERVIEW_ENABLE_STORE + " property of API at " + resourcePath
                            + "to true.");
                }
                artifact.setAttribute(Constants.API_OVERVIEW_ENABLE_STORE, "true");
                isResourceUpdated = true;
            }
            if (isResourceUpdated) {
                artifactManager.updateGenericArtifact(artifact);
            }
        } catch (UserStoreException | RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error occurred when updating API Artifact in registry", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error occurred when getting artifact manager", e);
        }
    }

    /**
     * This method updates the API properties so that they will be visible in the store by default
     * @param resourcePath
     */
    @Override
    public void updateAPIPropertyVisibility(String resourcePath) {
        try {
            Registry registry = getGovernanceRegistry();
            Resource resource = registry.get(resourcePath);
            boolean isResourceUpdated = false;
            if (log.isDebugEnabled()) {
                log.debug("Updating properties for registry path: " + resourcePath);
            }
            Properties map = resource.getProperties();
            List<String> modifiableKeys= new ArrayList<>();
            for (Object entry : map.keySet()) {
                String key = entry.toString();
                if (key.startsWith("api_meta.") && !key.endsWith("__display")) {
                    modifiableKeys.add(key);
                    isResourceUpdated = true;
                }
            }
            if (isResourceUpdated) {
                for (String modifiableKey : modifiableKeys) {
                    String newKey = modifiableKey + "__display";
                    if (log.isDebugEnabled()) {
                        log.debug("Replacing property: " + modifiableKey + " with property: " + newKey);
                    }
                    resource.addProperty(newKey, resource.getProperty(modifiableKey));
                    resource.removeProperty(modifiableKey);
                }
                registry.put(resourcePath, resource);
            }
        } catch (UserStoreException | RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error occurred when updating API Artifact in registry", e);
        }
    }
}
