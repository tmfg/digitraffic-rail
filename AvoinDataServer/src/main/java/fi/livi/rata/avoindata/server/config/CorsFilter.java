package fi.livi.rata.avoindata.server.config;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CorsFilter  implements Filter {

    @Override
    public void doFilter(final ServletRequest req, final ServletResponse res, final FilterChain chain) throws IOException, ServletException {
        final HttpServletResponse response = (HttpServletResponse) res;
        final HttpServletRequest request = (HttpServletRequest) req;

        //spring-websocket manages these
        if (!request.getRequestURI().contains("/websockets/")) {
            response.setHeader("Access-Control-Allow-Origin", "*");
        }
        chain.doFilter(req, res);
    }

    @Override
    public void init(final FilterConfig filterConfig) {}

    @Override
    public void destroy() {}
}
