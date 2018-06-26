package fi.livi.rata.avoindata.server.config;

import fi.livi.rata.avoindata.common.ESystemStateProperty;
import fi.livi.rata.avoindata.common.service.SystemStatePropertyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


@Component
public class DatabaseInitializationInterceptor extends HandlerInterceptorAdapter {
    private static final Set<String> INITIALIZATION_DISALLOWED_PARAMETER_NAMES = new HashSet<>(Arrays.asList("departure_date",
            "departureDate", "date"));

    private boolean hasDatabaseInitialized = false;

    @Autowired
    private SystemStatePropertyService systemStatePropertyService;

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response,
            final Object handler) throws IOException {
        if (hasDatabaseInitialized) {
            return true;
        }

        final Boolean isInLockedMode = systemStatePropertyService.getValueAsBoolean(ESystemStateProperty.DATABASE_LOCKED_MODE);
        if (isInLockedMode) {
            setErrorResponseText(response, "Tried to retrieve data, but database initialization is still in progress. Please wait.");
            return false;
        }

        final Boolean isLazyInitRunning = systemStatePropertyService.getValueAsBoolean(ESystemStateProperty.DATABASE_LAZY_INIT_RUNNING);
        if (isLazyInitRunning) {
            String url = request.getQueryString() == null ? "" : request.getQueryString();
            for (final String initializationDisallowedParameterName : INITIALIZATION_DISALLOWED_PARAMETER_NAMES) {
                if (url.contains(initializationDisallowedParameterName)) {
                    setErrorResponseText(response, "Cannot query non-live data while database initialization is in progress. Please wait.");
                    return false;
                }
            }
        } else {
            hasDatabaseInitialized = true;
        }

        return true;
    }

    private void setErrorResponseText(final HttpServletResponse response, final String text) throws IOException {
        final ServletOutputStream outputStream = response.getOutputStream();
        response.setStatus(503);
        outputStream.write(text.getBytes());
    }


}
