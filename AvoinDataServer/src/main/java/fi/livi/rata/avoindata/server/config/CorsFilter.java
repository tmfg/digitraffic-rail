package fi.livi.rata.avoindata.server.config;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CorsFilter  implements Filter {

    private static final String PREFLIGHT_MAX_AGE_MS = "86400"; // 1 day

    @Override
    public void doFilter(final ServletRequest req, final ServletResponse res, final FilterChain chain) throws IOException, ServletException {
        final HttpServletResponse response = (HttpServletResponse) res;
        final HttpServletRequest request = (HttpServletRequest) req;

        //spring-websocket manages these
        if (!request.getRequestURI().contains("/websockets/")) {
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "DNT,X-CustomHeader,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Content-Range,Range,Digitraffic-User");
            response.setHeader("Access-Control-Expose-Headers", "DNT,X-CustomHeader,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Content-Range,Range,Digitraffic-User");

            if (request.getMethod().toUpperCase().equals("OPTIONS")) {
                response.setHeader("Access-Control-Max-Age", PREFLIGHT_MAX_AGE_MS);
                response.setHeader("Content-Type", "text/plain; charset=utf-8");
                response.setHeader("Content-Length", "0");
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        }
        chain.doFilter(req, res);
    }

    @Override
    public void init(final FilterConfig filterConfig) {}

    @Override
    public void destroy() {}
}
