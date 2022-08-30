package org.wso2.carbon.apimgt.migration.validator.dao.constants;

public class SQLConstants {
    public static final String GET_API_ID_SQL =
            "SELECT     " +
                    "   API.API_ID " +
                    " FROM " +
                    "   AM_API API " +
                    " WHERE " +
                    "   API.API_PROVIDER = ? " +
                    "   AND API.API_NAME = ? " +
                    "   AND API.API_VERSION = ? ";

    public static final String GET_URL_TEMPLATES_BY_API_ID_SQL_260 =
            " SELECT " +
                    "   URL_PATTERN," +
                    "   HTTP_METHOD," +
                    "   AUTH_SCHEME," +
                    "   THROTTLING_TIER, " +
                    "   MEDIATION_SCRIPT " +
                    " FROM " +
                    "   AM_API_URL_MAPPING " +
                    " WHERE " +
                    "   API_ID = ? " +
                    " ORDER BY " +
                    "   URL_MAPPING_ID ASC ";
}
