package fi.livi.rata.avoindata.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ExecuteTimeInterceptor implements HandlerInterceptor {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    //before the actual handler will be executed
    @Override
    public boolean preHandle(final HttpServletRequest request,
                             final HttpServletResponse response,
                             final Object handler) {
        final long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);

        return true;
    }

    //after the handler is executed
    @Override
    public void postHandle(final HttpServletRequest request,
                           final HttpServletResponse response,
                           final Object handler,
                           final ModelAndView modelAndView) {

        final long startTime = (Long) request.getAttribute("startTime");
        final long endTime = System.currentTimeMillis();
        final long executeTime = endTime - startTime;
        if (executeTime > 1000) {
            log.debug("{}?{}: {} ms (HTTP {}, IP: {})", request.getRequestURI(), request.getQueryString(), executeTime, response.getStatus(), request.getRemoteAddr());
        }
    }
}
