/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.apimgt.migration.migrator.v420;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.migrator.commonMigrators.RegistryResourceMigrator;
import org.wso2.carbon.apimgt.migration.util.APIUtil;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.persistence.APIConstants;
import org.wso2.carbon.apimgt.persistence.utils.RegistryPersistenceUtil;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.governance.lcm.util.CommonUtil;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;

public class V420RegistryResourceMigrator extends RegistryResourceMigrator {
    private static final String TRANSITIONS = "Transitions";
    private static final Log log = LogFactory.getLog(V420RegistryResourceMigrator.class);
    private static final String CHECK_ITEMS = "CheckItems";
    private static final String STATES = "States";
    private static final String DATAMODEL = "datamodel";
    private static final String NAME = "name";
    private final String PROVIDER_PATH = "/apimgt/applicationdata/provider";
    private final String API_LIFECYCLE_ASPECT = "APILifeCycle";
    private static final String EVENT = "Event";
    private static final String TARGET = "Target";
    List<Tenant> tenants;

    public V420RegistryResourceMigrator(String rxtDir) throws UserStoreException {

        super(rxtDir);
        tenants = loadTenants();
    }

    private static JSONObject getTransitionObj(String event, String target) {

        JSONObject transitionObj = new JSONObject();
        transitionObj.put(EVENT, event);
        transitionObj.put(TARGET, target);
        return transitionObj;
    }

    public void migrate() throws APIMigrationException {

        super.migrate();
        registryDataPopulation();
    }

    private JSONObject convertLifecycleToJSON(String lcXML) throws APIMigrationException {
        log.info("WSO2 API-M Migration Task : Starting to convert the API LifeCycle XML to JSON");
        JSONObject LCConfigObj = new JSONObject();
        JSONArray statesArray = new JSONArray();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new APIMigrationException(e);
        }
        Document doc = null;
        try {
            doc = dBuilder.parse(new InputSource(new StringReader(lcXML)));
        } catch (SAXException | IOException e) {
            throw new APIMigrationException(e);
        }
        Element root = doc.getDocumentElement();
        NodeList states = root.getElementsByTagName("state");
        int nStates = states.getLength();
        JSONObject stateObj;
        for (int i = 0; i < nStates; i++) {
            stateObj = new JSONObject();
            Node node = states.item(i);
            Node id = node.getAttributes().getNamedItem("id");
            stateObj.put("State", id.getNodeValue());

            if (id != null && !id.getNodeValue().isEmpty()) {
                NodeList stateChildNodes = node.getChildNodes();
                int nItems = stateChildNodes.getLength();
                JSONArray transitionArray = new JSONArray();
                JSONArray checkListItems = new JSONArray();
                for (int j = 0; j < nItems; j++) {
                    Node transition = stateChildNodes.item(j);
                    // Add transitions
                    if ("transition".equals(transition.getNodeName())) {
                        Node target = transition.getAttributes().getNamedItem("target");
                        Node action = transition.getAttributes().getNamedItem("event");
                        if (target != null && action != null) {
                            transitionArray.add(getTransitionObj(action.getNodeValue(), target.getNodeValue()));
                        }
                    }
                    if (DATAMODEL.equals(transition.getNodeName())) {
                        NodeList datamodels = transition.getChildNodes();
                        int nDatamodel = datamodels.getLength();
                        for (int k = 0; k < nDatamodel; k++) {
                            Node dataNode = datamodels.item(k);
                            if (dataNode != null && dataNode.getAttributes() != null && "checkItems"
                                    .equals(dataNode.getAttributes().getNamedItem(NAME).getNodeValue())) {
                                NodeList items = dataNode.getChildNodes();
                                for (int x = 0; x < items.getLength(); x++) {
                                    Node item = items.item(x);
                                    if (item != null && item.getAttributes() != null
                                            && item.getAttributes().getNamedItem(NAME) != null) {
                                        checkListItems.add(item.getAttributes().getNamedItem(NAME).getNodeValue());
                                    }
                                }
                            }
                        }
                    }
                    if (transitionArray.size() > 0) {
                        stateObj.put(TRANSITIONS, transitionArray);
                    }
                    if (checkListItems.size() > 0) {
                        stateObj.put(CHECK_ITEMS, checkListItems);
                    }
                }
            }
            statesArray.add(stateObj);
            LCConfigObj.put(STATES, statesArray);
        }
        log.info("WSO2 API-M Migration Task : API LifeCycle XML to JSON Conversion Completed.");
        return LCConfigObj;
    }

    private void registryDataPopulation() throws APIMigrationException {

        log.info("WSO2 API-M Migration Task : Starting registry data migration for API Manager "
                + Constants.VERSION_4_2_0);

        boolean isError = false;
        boolean isTenantFlowStarted = false;
        for (Tenant tenant : tenants) {
            String tenantDomain = tenant.getDomain();
            int tenantId = tenant.getId();
            log.info("WSO2 API-M Migration Task : Starting registry data migration for tenant " + tenantId + '('
                    + tenantDomain + ')');

            try {
                PrivilegedCarbonContext.startTenantFlow();
                isTenantFlowStarted = true;
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId, true);
                ServiceHolder.getTenantRegLoader().loadTenantRegistry(tenantId);
                UserRegistry registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(tenantId);
                String defaultLifecyclePath = CommonUtil.getDefaltLifecycleConfigLocation() + File.separator
                        + APIConstants.API_LIFE_CYCLE + APIConstants.XML_EXTENSION;

                log.info("WSO2 API-M Migration Task : Starting data migration of Self Signup Configuration for tenant "
                        + tenantId + '(' + tenantDomain + ')');
                if (registry.resourceExists("/apimgt/applicationdata/sign-up-config.xml")) {
                    HashSet<String> signUpRoles = new HashSet<String>();
                    String currentConfig = ServiceReferenceHolder.getInstance().getApimConfigService()
                            .getTenantConfig(tenantDomain);
                    log.info("Current Config of tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
                    log.info(currentConfig);

                    JsonObject currentConfigJsonObject = (JsonObject) new JsonParser().parse(currentConfig);
                    if (currentConfigJsonObject.has(Constants.SELF_SIGNUP)) {
                        JsonObject currentSelfSignUp = (JsonObject) currentConfigJsonObject.get(Constants.SELF_SIGNUP);
                        JsonArray currentSignUpRoles = (JsonArray) currentSelfSignUp.get(Constants.SELF_SIGNUP_ROLES);
                        Iterator<JsonElement> currentSignUpRolesIterator = currentSignUpRoles.iterator();
                        while (currentSignUpRolesIterator.hasNext()) {
                            signUpRoles.add(currentSignUpRolesIterator.next().getAsString());
                        }
                        currentConfigJsonObject.remove(Constants.SELF_SIGNUP);
                    }

                    Resource resource = registry.get("/apimgt/applicationdata/sign-up-config.xml");
                    OMElement element = AXIOMUtil.stringToOM(
                            new String((byte[]) resource.getContent(), Charset.defaultCharset()));
                    JsonObject selfSignUpJsonObject = new JsonObject();
                    String signUpDomain = element.getFirstChildWithName(new QName("SignUpDomain")).getText();
                    OMElement rolesElement = element.getFirstChildWithName(new QName(Constants.SELF_SIGNUP_ROLES));
                    Iterator roleListIterator = rolesElement.getChildrenWithLocalName("SignUpRole");
                    while (roleListIterator.hasNext()) {
                        OMElement roleElement = (OMElement) roleListIterator.next();
                        boolean isExternalRole = Boolean.parseBoolean(
                                roleElement.getFirstChildWithName(new QName("IsExternalRole")).getText());
                        String roleName = roleElement.getFirstChildWithName(new QName("RoleName")).getText();
                        if (isExternalRole) {
                            signUpRoles.add(signUpDomain + "/" + roleName);
                        } else {
                            signUpRoles.add("Internal/" + roleName);
                        }
                    }

                    selfSignUpJsonObject.add(Constants.SELF_SIGNUP_ROLES,
                            new Gson().toJsonTree(signUpRoles).getAsJsonArray());
                    currentConfigJsonObject.add(Constants.SELF_SIGNUP, selfSignUpJsonObject);

                    // Prettify the tenant-conf
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String formattedTenantConf = gson.toJson(currentConfigJsonObject);
                    ServiceReferenceHolder.getInstance().getApimConfigService()
                            .updateTenantConfig(tenantDomain, formattedTenantConf);

                    // Log the updated tenant config
                    String updatedConfig = ServiceReferenceHolder.getInstance().getApimConfigService()
                            .getTenantConfig(tenantDomain);
                    log.info("Updated Config of tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
                    log.info(updatedConfig);

                    registry.delete("/apimgt/applicationdata/sign-up-config.xml");
                }
                log.info("WSO2 API-M Migration Task : Completed data migration of Self Signup Configuration for tenant "
                        + tenantId + '(' + tenantDomain + ')');
                log.info("WSO2 API-M Migration Task : Starting lifeCycle data migration for tenant " + tenantId + '('
                        + tenantDomain + ')');
                File file = new File(defaultLifecyclePath);
                if (file != null && file.exists()) {
                    String lcXML = FileUtils.readFileToString(file);
                    JSONObject States = convertLifecycleToJSON(lcXML);
                    String currentConfig = ServiceReferenceHolder.getInstance().getApimConfigService()
                            .getTenantConfig(tenantDomain);
                    JsonObject currentConfigJsonObject = (JsonObject) new JsonParser().parse(currentConfig);
                    JsonObject StateJsonObject = (JsonObject) new JsonParser().parse(States.toString());
                    currentConfigJsonObject.add("LifeCycle", StateJsonObject);
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    String formattedTenantConf = gson.toJson(currentConfigJsonObject);
                    ServiceReferenceHolder.getInstance().getApimConfigService()
                            .updateTenantConfig(tenantDomain, formattedTenantConf);
                } else {
                    throw new APIMigrationException("Default LifeCycle XML File Do Not Exist.");
                }

                GenericArtifactManager artifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);

                if (artifactManager != null) {
                    GenericArtifact[] artifacts = artifactManager.getAllGenericArtifacts();
                    log.info("Artifacts Length: " + artifacts.length);
                    for (GenericArtifact artifact : artifacts) {
                        String apiOverviewStatus = artifact.getAttribute(Constants.API_OVERVIEW_STATUS);
                        String lcState = artifact.getLifecycleState();
                        log.info("API_ID: " + artifact.getId() + " : API_OVERVIEW_STATUS: " + apiOverviewStatus + " lcState: " + lcState);
                        if (!apiOverviewStatus.equals(lcState) && lcState != null) {
                            artifact.setAttribute(Constants.API_OVERVIEW_STATUS, lcState);
                        }
                        if (artifact.getPath().contains(PROVIDER_PATH)) {
                            GenericArtifact apiArtifact = artifactManager.getGenericArtifact(artifact.getId());
                            API api = RegistryPersistenceUtil.getApiForPublishing(registry, apiArtifact);
                            APIIdentifier apiId = api.getId();
                            String path = RegistryPersistenceUtil.getAPIPath(apiId);
                            //Detaching the APILifeCycle
                            GovernanceUtils.removeAspect(path, API_LIFECYCLE_ASPECT, registry);
                        }
                    }
                } else {
                    throw new APIMigrationException("Error while migrating lifecycle xml - Artifact Manager is Null");
                }

            } catch (APIManagementException e) {
                log.error(
                        "WSO2 API-M Migration Task : Error occurred while migrating Self Signup Configuration for tenant "
                                + tenantId + '(' + tenantDomain + ')', e);
                isError = true;
                continue;
            } catch (RegistryException e) {
                log.error(
                        "WSO2 API-M Migration Task : Error occurred while accessing the registry for tenant " + tenantId
                                + '(' + tenantDomain + ')', e);
                isError = true;
                continue;
            } catch (XMLStreamException e) {
                log.error(
                        "WSO2 API-M Migration Task : Error occurred while converting the XML string to OMElement for tenant "
                                + tenantId + '(' + tenantDomain + ')', e);
                isError = true;
                continue;
            } catch (IOException e) {
                log.error("WSO2 API-M Migration Task : Error occurred while getting the Lifecycle XML ", e);
                isError = true;
                continue;
            } finally {
                if (isTenantFlowStarted) {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
            log.info("WSO2 API-M Migration Task : Completed registry data migration for tenant " + tenantId + '('
                    + tenantDomain + ')');
            log.info("WSO2 API-M Migration Task : Completed lifeCycle data migration for tenant " + tenantId + '('
                    + tenantDomain + ')');
        }
        if (isError) {
            throw new APIMigrationException(
                    "WSO2 API-M Migration Task : Error/s occurred during Registry data migration");
        } else {
            log.info("WSO2 API-M Migration Task : API registry data migration done for all the tenants");
        }
    }
}
