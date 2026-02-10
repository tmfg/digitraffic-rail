package fi.livi.rata.avoindata.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.orm.hibernate5.support.OpenSessionInViewInterceptor;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

 
import java.util.Set;

@Configuration
public class OSIVInterceptor extends OpenSessionInViewInterceptor {
    private final static String ATTRIBUTE_NAME = "USE_OSIV";

    private final static Set<String> OSIV_URL_LIST = Set.of(
        "/api/v1/compositions"
    );

    private static boolean useOsiv(final String requestPath) {
        return OSIV_URL_LIST.stream().anyMatch(requestPath::startsWith);
    }

    @Override
    public void preHandle(final WebRequest request) throws DataAccessException {
        final var requestPath = ((ServletWebRequest)request).getRequest().getRequestURI();

        if(useOsiv(requestPath)) {
            request.setAttribute(ATTRIBUTE_NAME, true, WebRequest.SCOPE_REQUEST);
            super.preHandle(request);
        }
    }

    @Override
    public void afterCompletion(final WebRequest request, @Nullable final Exception ex) throws DataAccessException {
        if(request.getAttribute(ATTRIBUTE_NAME, WebRequest.SCOPE_REQUEST) != null) {
            super.afterCompletion(request, ex);
        }
    }
}
