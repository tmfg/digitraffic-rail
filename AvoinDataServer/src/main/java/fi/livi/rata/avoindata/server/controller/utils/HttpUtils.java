package fi.livi.rata.avoindata.server.controller.utils;


import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtils {
    private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);

    public static String getFullURL(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        try {
            StringBuffer requestURL = request.getRequestURL();
            String queryString = request.getQueryString();

            if (queryString == null) {
                return requestURL.toString();
            } else {
                return requestURL.append('?').append(queryString).toString();
            }
        } catch (Exception e) {
            log.error("Error forming request url", e);
            return "";
        }
    }
}
