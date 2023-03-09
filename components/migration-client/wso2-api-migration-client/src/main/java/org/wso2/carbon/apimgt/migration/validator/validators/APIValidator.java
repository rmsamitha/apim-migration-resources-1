/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
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
package org.wso2.carbon.apimgt.migration.validator.validators;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIDefinition;
import org.wso2.carbon.apimgt.api.APIDefinitionValidationResponse;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIMgtResourceNotFoundException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.ErrorHandler;
import org.wso2.carbon.apimgt.api.FaultGatewaysException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.APIRevision;
import org.wso2.carbon.apimgt.api.model.APIRevisionDeployment;
import org.wso2.carbon.apimgt.api.model.Environment;
import org.wso2.carbon.apimgt.api.model.URITemplate;
import org.wso2.carbon.apimgt.api.model.VHost;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIConstants.OASResourceAuthTypes;
import org.wso2.carbon.apimgt.impl.dao.GatewayArtifactsMgtDAO;
import org.wso2.carbon.apimgt.impl.definitions.AsyncApiParserUtil;
import org.wso2.carbon.apimgt.impl.definitions.OASParserUtil;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.exception.ArtifactSynchronizerException;
import org.wso2.carbon.apimgt.impl.importexport.APIImportExportException;
import org.wso2.carbon.apimgt.impl.importexport.ExportFormat;
import org.wso2.carbon.apimgt.impl.utils.APIMWSDLReader;
import org.wso2.carbon.apimgt.impl.wsdl.model.WSDLValidationResponse;
import org.wso2.carbon.apimgt.impl.wsdl.util.SOAPOperationBindingUtils;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.migrator.Utility;
import org.wso2.carbon.apimgt.migration.util.APIUtil;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.validator.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.migration.validator.utils.Utils;
import org.wso2.carbon.apimgt.migration.validator.utils.V260Utils;
import org.wso2.carbon.apimgt.persistence.exceptions.APIPersistenceException;
import org.wso2.carbon.apimgt.persistence.utils.RegistryPersistenceUtil;
import org.wso2.carbon.apimgt.rest.api.common.RestApiCommonUtil;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.mappings.PublisherCommonUtils;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.GraphQLValidationResponseDTO;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The APIValidator class is used to validate all API related pre-validation steps.
 * Validations are done on one API and one pre-validation step at a time
 * using the {@link #validate} method.
 */
public class APIValidator {
    private Utils utils;
    private UserRegistry registry;
    private String apiName;
    private String apiVersion;
    private String provider;
    private String apiType;
    private String apiId;

    private String tenantDomain;

    private static final Log log = LogFactory.getLog(APIValidator.class);
    private final String saveSwagger = System.getProperty(Constants.preValidationService.SAVE_INVALID_DEFINITION);

    private org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO apiMgtDAO1
            = org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO.getInstance();


    public APIValidator(Utils utils) {
        this.utils = utils;
    }

    /**
     * @param registry         UserRegistry
     * @param artifact         Artifact corresponding to the API
     * @param preMigrationStep Pre-validation step to run
     * @throws GovernanceException if an error occurs while accessing artifact attributes
     */
    public void validate(String tenantDomain, UserRegistry registry, GenericArtifact artifact, String preMigrationStep)
            throws GovernanceException {
        this.registry = registry;
        this.apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
        this.apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
        this.provider = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);
        this.tenantDomain = tenantDomain;
        // At this point of  pre-validation step, SOAP and SOAPTOREST APIs from 2.6.0 will have their overview_type
        // set as HTTP, hence we are employing a Util method to fetch correct API Type based on other resources and
        // artifact fields.
        if (Constants.VERSION_2_6_0.equals(utils.getMigrateFromVersion())) {
            this.apiType = V260Utils.getAPIType(artifact);
        } else {
            this.apiType = artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE);
        }

        this.apiId = artifact.getId();
        if (Constants.preValidationService.API_DEFINITION_VALIDATION.equals(preMigrationStep)) {
            validateAPIDefinition();
        } else if (Constants.preValidationService.API_AVAILABILITY_VALIDATION.equals(preMigrationStep)) {
            validateApiAvailability();
        } else if (Constants.preValidationService.API_RESOURCE_LEVEL_AUTH_SCHEME_VALIDATION.equals(preMigrationStep)) {
            validateApiResourceLevelAuthScheme();
        } else if (Constants.preValidationService.API_DEPLOYED_GATEWAY_TYPE_VALIDATION.equals(preMigrationStep)) {
            validateApiDeployedGatewayType(artifact);
        }
    }

    public void validateAPIDefinition() {
        if (!utils.isStreamingAPI(apiType)) {
            validateOpenAPIDefinition();
            if (APIConstants.APITransportType.GRAPHQL.toString().equalsIgnoreCase(apiType)) {
                validateGraphQLAPIDefinition();
            } else if (APIConstants.API_TYPE_SOAP.equalsIgnoreCase(apiType)
                    || APIConstants.API_TYPE_SOAPTOREST.equalsIgnoreCase(apiType)) {
                validateWSDLDefinition();
            }
        } else {
            validateStreamingAPIDefinition();
        }
    }

    public void validateApiAvailability() {
        try {
            log.info("Validating API availability in db for API {name: " + apiName + ", version: " +
                    apiVersion + ", provider: " + provider + "}");
            int id = ApiMgtDAO.getInstance().getAPIID(provider, apiName, apiVersion);
            if (id == -1) {
                log.error("Unable to find the API " + "{name: " + apiName + ", version: " +
                        apiVersion + ", provider: " + provider + "} in the database");
            }
        } catch (SQLException e) {
            log.error("Error while getting the database connection ", e);
        }
    }

    public void validateOpenAPIDefinition() {
        String apiDefinition = null;
        APIDefinitionValidationResponse validationResponse = null;
        log.info("Validating open API definition of API {name: " + apiName + ", version: " +
                apiVersion + ", provider: " + provider + "}");
        try {
            apiDefinition = utils.getAPIDefinition(registry, apiName, apiVersion, provider, apiId);
            if (apiDefinition != null) {
                validationResponse = OASParserUtil.validateAPIDefinition(apiDefinition, Boolean.TRUE);
            }
        } catch (APIManagementException e) {
            log.error("Error while validating open API definition for " + apiName + " version: " + apiVersion
                    + " type: " + apiType, e);
        } catch (Exception e) {
            log.error("An unhandled exception has occurred while validating open API definition for " + apiName
                    + " version: " + apiVersion + " type: " + apiType, e);
        }
        if (validationResponse != null && !validationResponse.isValid()) {
            if (saveSwagger != null) {
                utils.saveInvalidDefinition(apiName, apiVersion, provider, apiId, apiDefinition);
            }
            for (ErrorHandler error : validationResponse.getErrorItems()) {
                log.error("OpenAPI Definition for API {name: " + apiName + ", version: " +
                        apiVersion + ", provider: " + provider + "}" + " is invalid. ErrorMessage: " +
                        error.getErrorMessage() + " ErrorDescription: " + error.getErrorDescription());
            }
        } else if (apiDefinition == null) {
            log.error("Error while validating open API definition for " + apiName + " version: " + apiVersion
                    + " type: " + apiType + ". Swagger definition of the API is missing...");
        } else {
            if (validationResponse != null) {
                APIDefinition parser = validationResponse.getParser();
                try {
                    if (parser != null) {
                        parser.getURITemplates(apiDefinition);
                    }
                    log.info("Successfully validated open API definition of " + apiName + " version: " + apiVersion
                            + " type: " + apiType);
                } catch (APIManagementException e) {
                    log.error("Error while retrieving URI Templates for " + apiName + " version: " + apiVersion
                            + " type: " + apiType, e);
                    if (saveSwagger != null) {
                        utils.saveInvalidDefinition(apiName, apiVersion, provider, apiId, apiDefinition);
                    }
                } catch (Exception e) {
                    log.error("Error while retrieving URI Templates for un handled exception " + apiName
                            + " version: " + apiVersion + " type: " + apiType, e);
                    if (saveSwagger != null) {
                        utils.saveInvalidDefinition(apiName, apiVersion, provider, apiId, apiDefinition);
                    }
                }
            } else {
                log.error("Error while validating open API definition for " + apiName + " version: " + apiVersion
                        + " type: " + apiType + " Validation response is null.");
                if (saveSwagger != null) {
                    utils.saveInvalidDefinition(apiName, apiVersion, provider, apiId, apiDefinition);
                }
            }
        }
    }

    private void validateGraphQLAPIDefinition() {
        GraphQLValidationResponseDTO graphQLValidationResponseDTO = null;
        log.info("Validating graphQL schema definition of " + apiName + " version: " + apiVersion + " type: "
                + apiType);
        try {
            String graphqlSchema = utils.getGraphqlSchemaDefinition(registry, apiName, apiVersion, provider, apiId);
            graphQLValidationResponseDTO = PublisherCommonUtils
                    .validateGraphQLSchema("schema.graphql", graphqlSchema);
        } catch (APIManagementException e) {
            log.error(" Error while validating graphql api definition for API:" + apiName
                    + " version: " + apiVersion + " " + e);
        }
        if (graphQLValidationResponseDTO != null && !graphQLValidationResponseDTO.isIsValid()) {
            log.error(" Invalid GraphQL definition found. " + "ErrorMessage: " + graphQLValidationResponseDTO
                    .getErrorMessage());
        } else {
            log.info("Successfully validated graphql schema of " + apiName + " version: " + apiVersion + "type: "
                    + apiType);
        }
    }

    private void validateWSDLDefinition() {
        WSDLValidationResponse wsdlValidationResponse = null;
        String wsdlArchivePath = utils.getWSDLArchivePath(apiName, apiVersion, provider);
        byte[] wsdl;
        log.info("Validating WSDL of " + apiName + " version: " + apiVersion + " type: " + apiType);
        try {
            if (registry.resourceExists(wsdlArchivePath)) {
                wsdl = (byte[]) registry.get(wsdlArchivePath).getContent();
                InputStream targetStream = new ByteArrayInputStream(wsdl);
                wsdlValidationResponse = APIMWSDLReader.extractAndValidateWSDLArchive(targetStream);
            } else {
                String wsdlPath = utils.getWSDLPath(apiName, apiVersion, provider);
                wsdl = (byte[]) registry.get(wsdlPath).getContent();
                wsdlValidationResponse = APIMWSDLReader.validateWSDLFile(wsdl);
            }
        } catch (RegistryException e) {
            log.error("Error while getting wsdl file", e);
        } catch (APIManagementException e) {
            log.error("Error while validating wsdl file of API:" + apiName + " version: " + apiVersion, e);
        }
        if (wsdlValidationResponse != null && !wsdlValidationResponse.isValid()) {
            log.error("Invalid WSDL definition found. " + wsdlValidationResponse.getError());
        } else {
            if (APIConstants.API_TYPE_SOAPTOREST.equalsIgnoreCase(apiType)) {
                int apiTenantId = 0;
                try {
                    apiTenantId = ServiceHolder.getRealmService().getTenantManager().getTenantId(tenantDomain);
                    Utility.startTenantFlow(tenantDomain, apiTenantId, MultitenantUtils
                            .getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenantDomain)));
                    log.info("WSO2 API-M SOAP TO REST" + "SOAP_TO_RET API exists. apiID=" + apiId + " apiName=" + apiName
                            + " apiVersion=" + apiVersion);
                    String swaggerStr = "";
                    //TODO://support wsdl zip as well
                    String wsdlPath = utils.getWSDLPath(apiName, apiVersion, provider);
                    wsdl = (byte[]) registry.get(wsdlPath).getContent();
                    swaggerStr = SOAPOperationBindingUtils.getSoapOperationMapping(wsdl);
                    String updatedSwagger = updateSwagger(apiId, swaggerStr, tenantDomain);
                    APIProvider apiProvider = RestApiCommonUtil.getLoggedInUserProvider();
                    //adding the api
                    API currentAPI = apiProvider.getAPIbyUUID(apiId, tenantDomain);
                    PublisherCommonUtils
                            .updateAPIBySettingGenerateSequencesFromSwagger(updatedSwagger, currentAPI, apiProvider,
                                    tenantDomain);
                    //adding the revision
                    APIRevision apiRevision = new APIRevision();
                    apiRevision.setApiUUID(apiId);
                    apiRevision.setDescription("Initial revision created in migration process");
                    String revisionId;
                    if (!StringUtils.equalsIgnoreCase(apiType, APIConstants.API_PRODUCT)) {
                        List<Environment> dynamicEnvironments = org.wso2.carbon.apimgt.migration.
                                migrator.v400.dao.ApiMgtDAO.getInstance().getAllEnvironments(tenantDomain);
                        Registry registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                        GenericArtifactManager tenantArtifactManager = APIUtil
                                .getArtifactManager(registry, APIConstants.API_KEY);
                        revisionId = addAPIRevision(apiRevision, tenantDomain, apiTenantId, apiProvider,
                                tenantArtifactManager);
                        // retrieve api artifacts
                        GenericArtifact apiArtifact = tenantArtifactManager
                                .getGenericArtifact(apiId);
                        List<APIRevisionDeployment> apiRevisionDeployments = new ArrayList<>();
                        String environments = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
                        String[] arrOfEnvironments = environments.split(",");
                        if ("none".equals(environments)) {
                            log.info("WSO2 API-M SOAP TO REST : No gateway environments are configured for API " +
                                    apiId + ". Hence revision deployment is skipped.");
                        } else {
                            for (String environment : arrOfEnvironments) {
                                APIRevisionDeployment apiRevisionDeployment = new APIRevisionDeployment();
                                apiRevisionDeployment.setRevisionUUID(revisionId);
                                apiRevisionDeployment.setDeployment(environment);
                                // Null VHost for the environments defined in deployment.toml (the default vhost)
                                apiRevisionDeployment.setVhost(null);
                                apiRevisionDeployment.setDisplayOnDevportal(true);
                                apiRevisionDeployments.add(apiRevisionDeployment);
                            }
                        }
                        String[] labels = apiArtifact.getAttributes(APIConstants.API_LABELS_GATEWAY_LABELS);
                        if (labels != null) {
                            for (String label : labels) {
                                if (Arrays.stream(arrOfEnvironments)
                                        .anyMatch(tomlEnv -> StringUtils.equals(tomlEnv, label))) {
                                    // if API is deployed to an environment and label with the same name,
                                    // discard deployment to dynamic environment
                                    continue;
                                }
                                Optional<Environment> optionalEnv = dynamicEnvironments.stream()
                                        .filter(e -> StringUtils.equals(e.getName(), label)).findFirst();
                                Environment dynamicEnv;
                                if (optionalEnv.isPresent()) {
                                    dynamicEnv = optionalEnv.get();
                                } else {
                                    log.error("WSO2 API-M SOAP TO REST Migration Task : Error while retrieving dynamic "
                                            + "environment of the label: " + label);
                                    return;
                                }
                                List<VHost> vhosts = dynamicEnv.getVhosts();
                                if (vhosts.size() > 0) {
                                    APIRevisionDeployment apiRevisionDeployment = new APIRevisionDeployment();
                                    apiRevisionDeployment.setRevisionUUID(revisionId);
                                    apiRevisionDeployment.setDeployment(dynamicEnv.getName());
                                    apiRevisionDeployment.setVhost(vhosts.get(0).getHost());
                                    apiRevisionDeployment.setDisplayOnDevportal(true);
                                    apiRevisionDeployments.add(apiRevisionDeployment);
                                } else {
                                    log.error("WSO2 API-M SOAP TO REST : Vhosts are empty for the dynamic "
                                            + "environment: " + dynamicEnv.getName());
                                    return;
                                }
                            }
                        }

                        if (!apiRevisionDeployments.isEmpty()) {
                            if (!StringUtils.equalsIgnoreCase(apiType, APIConstants.API_PRODUCT)) {
                                apiProvider
                                        .deployAPIRevision(apiId, revisionId, apiRevisionDeployments,
                                                tenantDomain);
                            } else {
                                apiProvider.deployAPIProductRevision(apiId, revisionId,
                                        apiRevisionDeployments);
                            }
                        }
                    }

                } catch (RegistryException e) {
                    throw new RuntimeException(e);
                } catch (APIManagementException e) {
                    throw new RuntimeException(e);
                } catch (FaultGatewaysException e) {
                    throw new RuntimeException(e);
                } catch (UserStoreException e) {
                    throw new RuntimeException(e);
                } catch (APIMigrationException e) {
                    throw new RuntimeException(e);
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
            log.info("Successfully validated wsdl file of " + apiName + " version: " + apiVersion + " type: "
                    + apiType);
        }
    }

    private String addAPIRevision(APIRevision apiRevision, String tenantDomain, int tenantId,
                                  APIProvider apiProviderTenant, GenericArtifactManager artifactManager)
            throws APIMigrationException {

        int revisionId = 0;
        String organization = tenantDomain;
        try {
            revisionId = apiMgtDAO1.getMostRecentRevisionId(apiRevision.getApiUUID()) + 1;
        } catch (APIManagementException e) {
            log.warn("WSO2 API-M SOAP TO REST : Couldn't retrieve mose recent revision Id from revision UUID: "
                    + apiRevision.getApiUUID() + " for tenant " + tenantId + '(' + tenantDomain + ')');
        }
        apiRevision.setId(revisionId);
        APIIdentifier apiId;
        try {
            apiId = APIUtil.getAPIIdentifierFromUUID(apiRevision.getApiUUID());
        } catch (APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M SOAP TO REST : Couldn't retrieve API Identifier for from "
                    + "revision UUID: " + apiRevision.getApiUUID() + " for tenant " + tenantId + '('
                    + tenantDomain + ')');
        }
        if (apiId == null) {
            throw new APIMigrationException("WSO2 API-M SOAP TO REST : Couldn't retrieve existing API with API "
                    + "UUID: " + apiRevision.getApiUUID() + " for tenant " + tenantId + '(' + tenantDomain
                    + ')');
        }
        apiId.setUuid(apiRevision.getApiUUID());
        String revisionUUID;
        try {
            revisionUUID = addAPIRevisionToRegistry(apiId.getUUID(), revisionId, tenantDomain, artifactManager);
        } catch (APIPersistenceException e) {
            throw new APIMigrationException("WSO2 API-M SOAP TO REST : Failed to add revision registry artifacts for"
                    + " API: " + apiId.getUUID() + " for tenant " + tenantId + '(' + tenantDomain + ')');
        }
        if (StringUtils.isEmpty(revisionUUID)) {
            throw new APIMigrationException("WSO2 API-M SOAP TO REST : Failed to retrieve revision from registry "
                    + "artifacts for API: " + apiId.getUUID() + " for tenant " + tenantId + '('
                    + tenantDomain + ')');
        } else {
            log.info("WSO2 API-M SOAP TO REST : Successfully added revision: "
                    + revisionUUID + " to registry for API: " + apiId.getUUID() + " for tenant " + tenantId
                    + '(' + tenantDomain + ')');
        }

        apiRevision.setRevisionUUID(revisionUUID);

        try {
            apiMgtDAO1.addAPIRevision(apiRevision);
            log.info("WSO2 API-M SOAP TO REST : Successfully added revision: " + revisionUUID + " to database for"
                    + " API: " + apiId.getUUID() + " for tenant " + tenantId + '(' + tenantDomain + ')');
        } catch (APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Failed to add revision to database artifacts "
                    + "for API: " + apiId.getUUID() + " revision uuid: " + revisionUUID + " for tenant "
                    + tenantId + '(' + tenantDomain + ')');
        }

        log.info("WSO2 API-M SOAP TO REST : Storing revision artifacts of API: " + apiRevision.getApiUUID()
                + " into gateway artifacts " + "and external server for tenant " + tenantId + '('
                + tenantDomain + ')');

        try {
            File artifact = ServiceHolder.getImportExportService()
                    .exportAPI(apiRevision.getApiUUID(), revisionUUID, true, ExportFormat.JSON, false, true,
                            organization);
            // Keeping the organization as tenant domain since MG does not support organization-wise deployment
            // Artifacts will be deployed in ST for all organizations
            GatewayArtifactsMgtDAO.getInstance()
                    .addGatewayAPIArtifactAndMetaData(apiRevision.getApiUUID(), apiId.getApiName(), apiId.getVersion(),
                            apiRevision.getRevisionUUID(), organization,
                            org.wso2.carbon.apimgt.impl.APIConstants.HTTP_PROTOCOL, artifact);
            if (ServiceHolder.getArtifactSaver() != null) {
                // Keeping the organization as tenant domain since MG does not support organization-wise deployment
                // Artifacts will be deployed in ST for all organizations
                ServiceHolder.getArtifactSaver().saveArtifact(apiRevision.getApiUUID(), apiId.getApiName(), apiId.getVersion(),
                        apiRevision.getRevisionUUID(), organization, artifact);
            }
            log.info("WSO2 API-M SOAP TO REST : Successfully added revision artifact of API: " + apiId.getUUID()
                    + " for tenant " + tenantId + '(' + tenantDomain + ')');
        } catch (APIImportExportException | ArtifactSynchronizerException | APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M SOAP TO REST : Error while Store the Revision Artifact "
                    + "for API: " + apiId.getUUID() + " for tenant " + tenantId + '(' + tenantDomain + ')', e);
        }
        return revisionUUID;
    }

    private String addAPIRevisionToRegistry(String apiUUID, int revisionId, String organization,
                                            GenericArtifactManager artifactManager)
            throws APIPersistenceException {
        String revisionUUID;
        boolean transactionCommitted = false;
        boolean tenantFlowStarted = false;
        try {
            GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiUUID);
            if (apiArtifact != null) {
                API api = RegistryPersistenceUtil.getApiForPublishing(registry, apiArtifact);
                APIIdentifier apiId = api.getId();
                String apiPath = RegistryPersistenceUtil.getAPIPath(apiId);
                int prependIndex = apiPath.lastIndexOf("/api");
                String apiSourcePath = apiPath.substring(0, prependIndex);
                String revisionTargetPath = RegistryPersistenceUtil.getRevisionPath(apiId.getUUID(), revisionId);
                if (registry.resourceExists(revisionTargetPath)) {
                    log.warn("WSO2 API-M SOAP TO REST : API revision already exists with id: " + revisionId);
                } else {
                    registry.copy(apiSourcePath, revisionTargetPath);
                    registry.commitTransaction();
                    transactionCommitted = true;

                    String logMessage = "WSO2 API-M SOAP TO REST : Revision for API Name: " + apiId.getApiName()
                            + ", " + "API Version " + apiId.getVersion() + " created";
                    log.info(logMessage);

                }
                Resource apiRevisionArtifact = registry.get(revisionTargetPath + "api");
                revisionUUID = apiRevisionArtifact.getUUID();

            } else {
                String msg = "WSO2 API-M SOAP TO REST : Failed to get API. API artifact corresponding to artifactId "
                        + apiUUID + " does not exist";
                throw new APIMgtResourceNotFoundException(msg);
            }
        } catch (RegistryException e) {
            try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                // Throwing an error here would mask the original exception
                log.error("WSO2 API-M SOAP TO REST : Error while rolling back the transaction for API Revision "
                        + "create for API: " + apiUUID, re);
            }
            throw new APIPersistenceException("WSO2 API-M SOAP TO REST : Error while performing registry transaction"
                    + " operation", e);
        } catch (APIManagementException e) {
            throw new APIPersistenceException("WSO2 API-M SOAP TO REST : Error while creating API Revision", e);
        } finally {
            try {
                if (tenantFlowStarted) {
                    RegistryPersistenceUtil.endTenantFlow();
                }
                if (!transactionCommitted) {
                    registry.rollbackTransaction();
                }
            } catch (RegistryException ex) {
                throw new APIPersistenceException("WSO2 API-M SOAP TO REST : Error while rolling back the transaction"
                        + " for API Revision create for API: " + apiUUID, ex);
            }
        }
        return revisionUUID;
    }

    private String updateSwagger(String apiId, String apiDefinition, String organization)
            throws APIManagementException, FaultGatewaysException {
        APIDefinitionValidationResponse response = OASParserUtil
                .validateAPIDefinition(apiDefinition, true);
        if (!response.isValid()) {
            throw new RuntimeException("Error");
        }
        return PublisherCommonUtils.updateSwagger(apiId, response, false, organization);

    }

    private void validateStreamingAPIDefinition() {
        if ("4.0.0".equals(utils.getMigrateFromVersion())) {
            String apiPath = null;
            String asyncAPIDefinition = "";
            APIDefinitionValidationResponse validationResponse = null;
            log.info("Validating streaming api definition of " + apiName + " version: " + apiVersion + " type: "
                    + apiType);
            try {
                apiPath = GovernanceUtils.getArtifactPath(registry, apiId);
            } catch (GovernanceException e) {
                log.error(" Error while getting AsyncAPI definition. " + e);
            }
            if (apiPath != null) {
                int prependIndex = apiPath.lastIndexOf("/api");
                String apiSourcePath = apiPath.substring(0, prependIndex);
                String definitionPath = apiSourcePath + "/" + "asyncapi.json";
                try {
                    if (registry.resourceExists(definitionPath)) {
                        Resource apiDocResource = registry.get(definitionPath);
                        asyncAPIDefinition = new String((byte[]) apiDocResource.getContent(),
                                Charset.defaultCharset());
                    }
                } catch (RegistryException e) {
                    log.error(" Error while getting AsyncAPI definition for API: " + apiName + " version: " + apiVersion
                            , e);
                }

                if (!asyncAPIDefinition.isEmpty()) {
                    try {
                        validationResponse = AsyncApiParserUtil.validateAsyncAPISpecification(asyncAPIDefinition, true);
                    } catch (APIManagementException e) {
                        log.error(" Error while validating AsyncAPI definition for API:" + apiName + " version: "
                                + apiVersion, e);
                    }
                    if (validationResponse != null && !validationResponse.isValid()) {
                        log.error(" Invalid AsyncAPI definition found. " + validationResponse.getErrorItems());
                    }
                }
                log.info("Validating streaming api definition of API:" + apiName + " version: " + apiVersion
                        + " type: " + apiType);
            } else {
                log.error("apiPath of  " + apiName + " version: " + apiVersion + " is null");
            }
        } else {
            log.info("API " + apiName + " version: " + apiVersion + " type: " + apiType + " was not validated "
                    + "since the AsyncAPI definitions are supported after API Manager 4.0.0");
        }
    }

    public void validateApiResourceLevelAuthScheme() {

        Pattern pattern = Pattern.compile("2\\.\\d\\.\\d");

        if (pattern.matcher(utils.getMigrateFromVersion()).matches()) {
            log.info("Validating Resource Level Auth Scheme of API {name: " + apiName + ", version: " + apiVersion
                    + ", provider: " + provider + "}");
            try {
                int id = ApiMgtDAO.getInstance().getAPIID(provider, apiName, apiVersion);
                Set<URITemplate> uriTemplates = ApiMgtDAO.getInstance().getURITemplatesByAPIID(id);
                for (URITemplate uriTemplate : uriTemplates) {
                    if (!(OASResourceAuthTypes.APPLICATION_OR_APPLICATION_USER.equals(uriTemplate.getAuthType())
                            || OASResourceAuthTypes.NONE.equals(uriTemplate.getAuthType())
                            || "Any".equals(uriTemplate.getAuthType()))) {
                        log.warn("Resource level Authentication Schemes 'Application', 'Application User' are not " +
                                "supported, Resource {HTTP verb: " + uriTemplate.getHTTPVerb() + ", URL pattern: " +
                                uriTemplate.getUriTemplate() + ", Auth Scheme: " + uriTemplate.getAuthType() + "}");
                    }
                }
            } catch (SQLException e) {
                log.error("Error on Retrieving URITemplates for apiResourceLevelAuthSchemeValidation", e);
            }
            log.info("Completed Validating Resource Level Auth Scheme of API {name: " + apiName + ", version: "
                    + apiVersion + ", provider: " + provider + "}");
        }


    }

    public void validateApiDeployedGatewayType(GenericArtifact apiArtifact) {
        log.info("Validating deployed gateway type for API {name: " + apiName + ", version: " + apiVersion
                + ", provider: " + provider + "}");
        try {
            String environments = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
            if ("none".equals(environments)) {
                log.warn("No gateway environments are configured for API {name: " + apiName + ", version: " + apiVersion
                        + ", provider: " + provider + "}. Hence revision deployment will be skipped at migration");
            }
            log.info("Completed deployed gateway type validation for API {name: " + apiName + ", version: " + apiVersion
                    + ", provider: " + provider + "}");
        } catch (GovernanceException e) {
            log.error("Error on retrieving API Gateway environment from API generic artifact", e);
        }

    }
}
