package fi.livi.rata.avoindata.updater.config;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

@Component
public class ApiKeyValidationFilter extends GenericFilterBean {

    final String ramiApiKey;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public ApiKeyValidationFilter(@Value("${rami.api-key}") final String key) {
        this.ramiApiKey = key;
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) {

        final HttpServletRequest req = (HttpServletRequest) request;

        final String path = req.getRequestURI();

        if (!path.startsWith("/rami")) {
            try {
                chain.doFilter(request, response);
                return;
            } catch (final IOException | ServletException error) {
                logger.error(error.getMessage());
            }
        }

        final String apiKey = req.getHeader("API-KEY") == null ? "" : req.getHeader("API-KEY");

        if (apiKey.equals(ramiApiKey)) {
            try {
                chain.doFilter(request, response);
                return;
            } catch (final IOException | ServletException error) {
                logger.error(error.getMessage());
            }
        } else {
            final HttpServletResponse resp = (HttpServletResponse) response;
            final String errorMessage = "Invalid API-KEY";

            resp.reset();
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentLength(errorMessage.length());

            try {
                resp.getWriter().write(errorMessage);
            } catch (final IOException error) {
                logger.error(error.getMessage());
            }
        }
    }
}

