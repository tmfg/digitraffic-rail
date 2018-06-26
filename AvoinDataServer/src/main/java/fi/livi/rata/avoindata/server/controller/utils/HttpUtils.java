package fi.livi.rata.avoindata.server.controller.utils;


import javax.servlet.http.HttpServletRequest;

public class HttpUtils {
    public static String getFullURL(HttpServletRequest request) {
        if (request == null) {
            return "";
        }

        StringBuffer requestURL = request.getRequestURL();
        String queryString = request.getQueryString();

        if (queryString == null) {
            return requestURL.toString();
        } else {
            return requestURL.append('?').append(queryString).toString();
        }
    }
}
