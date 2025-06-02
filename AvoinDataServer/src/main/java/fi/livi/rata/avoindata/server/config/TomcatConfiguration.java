package fi.livi.rata.avoindata.server.config;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.Constants;
import org.apache.catalina.valves.JsonErrorReportValve;
import org.apache.commons.lang3.StringUtils;
import org.apache.coyote.ActionCode;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.apache.tomcat.util.json.JSONFilter;
import org.apache.tomcat.util.res.StringManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.websocket.servlet.TomcatWebSocketServletWebServerCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.stereotype.Component;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;
import fi.livi.rata.avoindata.server.controller.DefaultExceptionHandler;

/**
 * Got some inspiration for the implementation from
 * <a href="https://github.com/spring-projects/spring-boot/issues/21257">
 * Allow custom ErrorReportValve to be used with Tomcat and provide whitelabel version
 * </a>
 */
@Component
public class TomcatConfiguration extends TomcatWebSocketServletWebServerCustomizer {

    private final DefaultExceptionHandler defaultExceptionHandler;

    @Autowired
    public TomcatConfiguration(final DefaultExceptionHandler defaultExceptionHandler) {
        this.defaultExceptionHandler = defaultExceptionHandler;
    }

    @Override
    public void customize(final TomcatServletWebServerFactory factory) {
        factory.addContextCustomizers((context) -> {
            // Handle IllegalArgumentException in lower level if it happens in Tomcat and not in the controller level
            if (context.getParent() instanceof final StandardHost parent) {
                parent.setErrorReportValveClass(CustomErrorReportValve.class.getName());
                parent.addValve(new CustomErrorReportValve(defaultExceptionHandler));
            }
        });
        // Allows path variable to have URL Encoded value of e.g. the forward slash (/ => %2F)
        factory.addConnectorCustomizers(
                connector -> connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue()));
    }

    @Override
    public int getOrder() {
        return 100; // needs to be AFTER the one configured with TomcatWebServerFactoryCustomizer
    }

    /**
     * Modified code from super class to report IllegalArgumentException as json and not as html with internal server error.
     */
    private static class CustomErrorReportValve extends JsonErrorReportValve {

        private final DefaultExceptionHandler exceptionHandler;

        CustomErrorReportValve(final DefaultExceptionHandler exceptionHandler) {
            super();
            this.exceptionHandler = exceptionHandler;
        }

        @Override
        protected void report(final Request request, final Response response, final Throwable throwable) {
            if (throwable instanceof IllegalArgumentException) {
                reportIllegalArgumentException(request, response, (IllegalArgumentException) throwable);
                return;
            }
            super.report(request, response, throwable);
        }

        private void reportIllegalArgumentException(final Request request, final Response response,
                                                    final IllegalArgumentException illegalArgumentException) {

            final ExceptionMessage em = exceptionHandler.handleIllegalArgumentException(illegalArgumentException, response, request);
            final int statusCode = response.getStatus();

            // Do nothing on a 1xx, 2xx and 3xx status
            // Do nothing if anything has been written already
            // Do nothing if the response hasn't been explicitly marked as in error
            // and that error has not been reported.
            if (statusCode < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
                return;
            }

            // If an error has occurred that prevents further I/O, don't waste time
            // producing an error report that will never be read
            final AtomicBoolean result = new AtomicBoolean(false);
            response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, result);
            if (!result.get()) {
                return;
            }

            final StringManager smClient = StringManager.getManager(Constants.Package, request.getLocales());
            response.setLocale(smClient.getLocale());

            // {
            //   "queryString": "a=b",
            //   "code": "ILLEGAL_ARGUMENT_EXCEPTION",
            //   "errorMessage": "Invalid parameter type"
            // }
            final String queryStringMessage =
                    StringUtils.isNotBlank(em.queryString) ? StringUtil.format("  \"queryString\": \"{}\",\n", JSONFilter.escape(em.queryString)) :
                    "";
            final String jsonReport =
                    StringUtil.format("{\n{}  \"code\": \"{}\",\n" + "  \"errorMessage\": \"" + JSONFilter.escape(em.errorMessage) + "\"\n" + "}",
                            queryStringMessage, em.code);
            try {
                try {
                    response.setContentType("application/json");
                    response.setCharacterEncoding("utf-8");
                } catch (final Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    if (container.getLogger().isDebugEnabled()) {
                        container.getLogger().debug(sm.getString("errorReportValve.contentTypeFail"), t);
                    }
                }
                final Writer writer = response.getReporter();
                if (writer != null) {
                    writer.write(jsonReport);
                    response.finishResponse();
                }
            } catch (final IOException | IllegalStateException e) {
                // Ignore
            }
        }
    }
}
