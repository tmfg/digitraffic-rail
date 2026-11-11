package fi.livi.rata.avoindata.server.config;

import java.io.IOException;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Some clients call these urls with a trailing slash. Handle them as if the slash was not there
 * and log a warning, so we can see when the calls stop.
 */
@Component
public class TrailingSlashFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TrailingSlashFilter.class);

    // Not handled on purpose, these were also seen with a trailing slash:
    // - /api/v1/live-trains/ and /api/v1/live-trains/{train_number}/
    private static final Set<String> PATHS = Set.of(
            WebConfig.CONTEXT_PATH + "train-locations/latest",
            WebConfig.CONTEXT_PATH + "train-locations.geojson/latest",
            WebConfig.CONTEXT_PATH + "train-tracking");

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final @NonNull HttpServletResponse response,
                                    final @NonNull FilterChain chain) throws ServletException, IOException {
        final String uri = request.getRequestURI();

        if (uri.length() > 1 && uri.endsWith("/")) {
            final String withoutSlash = uri.substring(0, uri.length() - 1);

            if (PATHS.contains(withoutSlash)) {
                log.warn("method=doFilterInternal Request with trailing slash uri={} digitrafficUser={} userAgent={}",
                        uri, request.getHeader("Digitraffic-User"), request.getHeader("User-Agent"));
                chain.doFilter(new WithoutTrailingSlash(request, withoutSlash), response);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private static class WithoutTrailingSlash extends HttpServletRequestWrapper {
        private final String uri;

        WithoutTrailingSlash(final HttpServletRequest request, final String uri) {
            super(request);
            this.uri = uri;
        }

        @Override
        public String getRequestURI() {
            return uri;
        }

        @Override
        public String getServletPath() {
            return uri.substring(getContextPath().length());
        }

        @Override
        public StringBuffer getRequestURL() {
            final StringBuffer url = super.getRequestURL();
            return !url.isEmpty() && url.charAt(url.length() - 1) == '/' ? url.deleteCharAt(url.length() - 1) : url;
        }
    }
}
