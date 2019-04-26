package fi.livi.rata.avoindata.server.config;


import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ExecuteTimeInterceptor extends HandlerInterceptorAdapter {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    //before the actual handler will be executed
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);

        return true;
    }

    //after the handler is executed
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {

        long startTime = (Long) request.getAttribute("startTime");
        long endTime = System.currentTimeMillis();
        long executeTime = endTime - startTime;
        if (executeTime > 1000) {
            Entity traceEntity = AWSXRay.getTraceEntity();
            String traceId = traceEntity != null && traceEntity.getTraceId() != null ? traceEntity.getTraceId().toString() : "";
            log.debug("{}?{}: {} ms (HTTP {}, IP: {}, Trace: {})", request.getRequestURI(), request.getQueryString(), executeTime, response.getStatus(), request.getRemoteAddr(), traceId);
        }
    }
}
