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
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.validator.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.migration.validator.dto.ApplicationDTO;
import org.wso2.carbon.apimgt.migration.validator.dto.ApplicationKeyMappingDTO;
import org.wso2.carbon.apimgt.migration.validator.utils.Utils;

import java.util.Set;
import java.util.regex.Pattern;

public class ApplicationValidator {
    private static final Log log = LogFactory.getLog(ApplicationValidator.class);
    protected Utils utils;

    public ApplicationValidator(Utils utils) {
        this.utils = utils;
    }

    public void validate(ApplicationDTO application, String preMigrationStep) {
        if (Constants.preValidationService.APP_THIRD_PARTY_KM_VALIDATION.equals(preMigrationStep)) {
            validateAppThirdPartyKMUsage(application);
        }
    }

    public void validateAppThirdPartyKMUsage(ApplicationDTO application) {
        Pattern pattern = Pattern.compile("(2\\.\\d\\.\\d)|(3\\.0\\.0)|(3\\.1\\.0)");
        if (pattern.matcher(utils.getMigrateFromVersion()).matches()) {
            log.info("Validating third party key manager usage for application: " + application.getName()
                    + ", subscriber: " + application.getSubscriberId());

            Set<ApplicationKeyMappingDTO> applicationKeyMappings = ApiMgtDAO
                    .getInstance()
                    .getKeyMappingFromApplicationId(application.getApplicationId());
            if (!applicationKeyMappings.isEmpty()) {
                for (ApplicationKeyMappingDTO applicationKeyMapping : applicationKeyMappings) {
                    boolean isThirdPartyKMUsed = ApiMgtDAO.getInstance()
                            .isThirdPartyKeyManagerUsed(applicationKeyMapping.getConsumerKey());
                    if (isThirdPartyKMUsed) {
                        log.warn("Usage of third party key manager detected for "
                                + "application: " + application.getName()
                                + ", subscriber: " + application.getSubscriberId()
                                + ", key type: " + applicationKeyMapping.getKeyType()
                                + ". You may need to reconfigure the third party key manager for latest version"
                        );
                    } else {
                        log.info("Third Party key manager usage validation complete for"
                                + " application: " + application.getName()
                                + ", subscriber: " + application.getSubscriberId()
                                + ", key type: " + applicationKeyMapping.getKeyType());
                    }
                }
            } else {
                log.info("Third Party key manager usage validation complete, "
                        + "Keys are not generated for application: " + application.getName()
                        + ", subscriber: " + application.getSubscriberId());
            }
        }
    }
}
