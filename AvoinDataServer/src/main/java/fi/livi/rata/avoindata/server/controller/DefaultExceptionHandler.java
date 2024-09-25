package fi.livi.rata.avoindata.server.controller;

import java.sql.SQLTimeoutException;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;
import fi.livi.rata.avoindata.server.controller.api.exception.AbstractException;
import fi.livi.rata.avoindata.server.controller.api.exception.AbstractNotFoundException;
import fi.livi.rata.avoindata.server.controller.utils.CacheControl;
import fi.livi.rata.avoindata.server.controller.utils.HttpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
@ResponseBody
public class DefaultExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @ExceptionHandler(AbstractNotFoundException.class)
    @ResponseStatus(HttpStatus.OK)
    public ExceptionMessage handleException(final AbstractNotFoundException e,
                                            final HttpServletResponse response,
                                            final HttpServletRequest request) {
        return createAndLogReturn(request, response, e.getMessage(), e.getCode());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ExceptionMessage handleHttpMediaTypeNotAcceptableException(final HttpMediaTypeNotAcceptableException e,
                                                                      final HttpServletResponse response,
                                                                      final HttpServletRequest request) {
        CacheControl.clearCacheMaxAgeSeconds(response);

        return createAndLogReturn(request, response, "HttpMediaTypeNotAcceptableException", ExceptionMessage.ErrorCodeEnum.INTERNAL_ERROR);
    }

    private ExceptionMessage createAndLogReturn(final HttpServletRequest request,
                                                final HttpServletResponse response,
                                                final String message,
                                                final ExceptionMessage.ErrorCodeEnum code) {
        setResponseTypeToJson(response);
        final ExceptionMessage exceptionMessage = new ExceptionMessage(message, code, request.getRequestURL().toString(),
                request.getQueryString());
        log.debug("Debug exception {}", exceptionMessage);
        return exceptionMessage;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ExceptionMessage handleIllegalArgumentException(final IllegalArgumentException e,
                                                           final HttpServletResponse response,
                                                           final HttpServletRequest request) {
        setResponseTypeToJson(response);
        log.info(String.format("Threw IllegalArgumentException from url %s?%s", request.getRequestURL().toString(), request.getQueryString()), e);
        return new ExceptionMessage(e.getMessage(), ExceptionMessage.ErrorCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION, request.getRequestURL().toString(),
                request.getQueryString());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ExceptionMessage handleNoResourceFoundException(final NoResourceFoundException e,
                                                           final HttpServletResponse response,
                                                           final HttpServletRequest request) {
        setResponseTypeToJson(response);
        log.info(String.format("Threw NoResourceFoundException from url %s?%s", request.getRequestURL().toString(), request.getQueryString()), e);
        return new ExceptionMessage("Resource not found", ExceptionMessage.ErrorCodeEnum.RESOURCE_NOT_FOUND, request.getRequestURL().toString(),
                request.getQueryString());
    }

    @ExceptionHandler(AbstractException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ExceptionMessage handleException(final AbstractException e,
                                            final HttpServletResponse response,
                                            final HttpServletRequest request) {
        setResponseTypeToJson(response);
        log.info(String.format("Threw AbstractException from url %s?%s", request.getRequestURL().toString(), request.getQueryString()), e);
        return new ExceptionMessage(e.getMessage(), e.getCode(), request.getRequestURL().toString(), request.getQueryString());
    }

    @ExceptionHandler(TypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ExceptionMessage handleConversionFailedException(final TypeMismatchException e,
                                                            final HttpServletResponse response,
                                                            final HttpServletRequest request) {
        final String value = e.getValue().toString();
        final String targetTypeName = e.getRequiredType().getSimpleName();

        return createAndLogReturn(request, response,
                String.format("Invalid format for parameter. Target type: %s, parameter: %s", targetTypeName, value),
                ExceptionMessage.ErrorCodeEnum.PARAMETER_WRONG_TYPE);
    }

    @ExceptionHandler(JpaSystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ExceptionMessage handleJpaSystemException(final JpaSystemException e,
                                                     final HttpServletResponse response,
                                                     final HttpServletRequest request) {
        if (e.getCause().getCause() instanceof SQLTimeoutException) {
            log.error(HttpUtils.getFullURL(request), e);
            return createAndLogReturn(request, response, "Server load too high. Please try again later",
                    ExceptionMessage.ErrorCodeEnum.TOO_MUCH_LOAD_IN_SYSTEM);
        } else {
            return handleRuntimeException(e, response, request);
        }
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ExceptionMessage handleMissingServletRequestParameterException(
            final MissingServletRequestParameterException e,
            final HttpServletResponse response,
            final HttpServletRequest request) {
        log.debug("Debug handleMissingServletRequestParameterException exception {}", e);
        return createAndLogReturn(request, response,
                String.format("The request was missing mandatory parameter. Parameter name is '%s' and type '%s'. Url: %s",
                        e.getParameterName(), e.getParameterType(), HttpUtils.getFullURL(request)),
                ExceptionMessage.ErrorCodeEnum.MISSING_MANDATORY_PARAMETER);
    }

    @ExceptionHandler({ ClientAbortException.class })
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ExceptionMessage handleClientAbortException(final ClientAbortException e,
                                                       final HttpServletResponse response,
                                                       final HttpServletRequest request) {
        log.warn("HandleClientAbortException exception {}", e);
        return createAndLogReturn(request, response, "Client aborted connection error", ExceptionMessage.ErrorCodeEnum.INTERNAL_ERROR);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public void handleAsyncRequestNotUsableException(final AsyncRequestNotUsableException e, final HttpServletResponse response,
                                                     final HttpServletRequest request) {
        if (e.getCause() instanceof ClientAbortException) {
            handleClientAbortException((ClientAbortException) e.getCause(), response, request);
        }
    }

    @ExceptionHandler({ Exception.class, RuntimeException.class })
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public ExceptionMessage handleRuntimeException(final Exception e,
                                                   final HttpServletResponse response,
                                                   final HttpServletRequest request) {
        log.error(HttpUtils.getFullURL(request), e);
        return createAndLogReturn(request, response, "Internal error", ExceptionMessage.ErrorCodeEnum.INTERNAL_ERROR);
    }

    private static void setResponseTypeToJson(final HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    }

}
