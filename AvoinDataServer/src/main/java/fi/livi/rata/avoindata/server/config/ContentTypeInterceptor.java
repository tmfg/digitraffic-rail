package fi.livi.rata.avoindata.server.config;


import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sets content type to application/json without charset information
 */
@Component
public class ContentTypeInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler,
            final ModelAndView modelAndView) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    }
}
