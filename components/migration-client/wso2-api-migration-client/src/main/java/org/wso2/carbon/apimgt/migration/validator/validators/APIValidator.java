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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIDefinition;
import org.wso2.carbon.apimgt.api.APIDefinitionValidationResponse;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.ErrorHandler;
import org.wso2.carbon.apimgt.api.model.URITemplate;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIConstants.OASResourceAuthTypes;
import org.wso2.carbon.apimgt.impl.definitions.AsyncApiParserUtil;
import org.wso2.carbon.apimgt.impl.definitions.OASParserUtil;
import org.wso2.carbon.apimgt.impl.utils.APIMWSDLReader;
import org.wso2.carbon.apimgt.impl.wsdl.model.WSDLValidationResponse;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.validator.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.migration.validator.utils.Utils;
import org.wso2.carbon.apimgt.migration.validator.utils.V260Utils;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.mappings.PublisherCommonUtils;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.GraphQLValidationResponseDTO;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.SQLException;
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
    private static final Log log = LogFactory.getLog(APIValidator.class);
    private final String saveSwagger = System.getProperty(Constants.preValidationService.SAVE_INVALID_DEFINITION);

    public APIValidator(Utils utils) {
        this.utils = utils;
    }

    /**
     * @param registry         UserRegistry
     * @param artifact         Artifact corresponding to the API
     * @param preMigrationStep Pre-validation step to run
     * @throws GovernanceException if an error occurs while accessing artifact attributes
     */
    public void validate(UserRegistry registry, GenericArtifact artifact, String preMigrationStep)
            throws GovernanceException {
        this.registry = registry;
        this.apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
        this.apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
        this.provider = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);

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
            log.info("Successfully validated wsdl file of " + apiName + " version: " + apiVersion + " type: "
                    + apiType);
        }
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
