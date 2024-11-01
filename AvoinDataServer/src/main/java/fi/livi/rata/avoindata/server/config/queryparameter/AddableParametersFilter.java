package fi.livi.rata.avoindata.server.config.queryparameter;

import org.springframework.stereotype.Component;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;

@Component
public class AddableParametersFilter implements Filter {
    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {

    }

    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
                         final FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest instanceof final HttpServletRequest request) {
            final HashMap<String, String[]> extraParams = new HashMap<>();

            final String date = request.getParameter("date");
            if (date != null) {
                extraParams.put("departure_date", new String[]{date});
            }

            final String departure_date = request.getParameter("departure_date");
            if (departure_date != null) {
                extraParams.put("date", new String[]{departure_date});
            }

            filterChain.doFilter(new ModifiableHttpServletRequestWrapper(request, extraParams), servletResponse);
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}