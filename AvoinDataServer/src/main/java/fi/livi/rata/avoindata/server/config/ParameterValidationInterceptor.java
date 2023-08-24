package fi.livi.rata.avoindata.server.config;


import com.google.common.base.CaseFormat;
import fi.livi.rata.avoindata.server.controller.api.exception.UnknownParametersException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Check for unknown query-parameters
 */
@Component
public class ParameterValidationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) {
        if (handler instanceof HandlerMethod) {
            final HandlerMethod handlerMethod = (HandlerMethod) handler;
            final MethodParameter[] methodParameters = handlerMethod.getMethodParameters();
            final Set<String> methodParameterNames = Arrays.stream(methodParameters).map(MethodParameter::getParameterName).collect(
                    Collectors.toSet());

            // This is needed since parameter-names evaluate to null until class is loaded for the first time
            if (methodParameterNames.contains(null)) {
                return true;
            }

            for (final String parameterName : request.getParameterMap().keySet()) {
                final String camelParameterName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, parameterName);
                final boolean isMethodFound = methodParameterNames.contains(parameterName) || methodParameterNames.contains(
                        camelParameterName);
                if (!isMethodFound && !isDateAlias(methodParameterNames, parameterName)) {
                    throw new UnknownParametersException(request, parameterName);
                }
            }

            return true;
        } else {
            return true;
        }
    }

    private boolean isDateAlias(final Set<String> methodParameterNames, final String parameterName) {
        return methodParameterNames.contains("departure_date") && parameterName.equals("date");
    }
}
