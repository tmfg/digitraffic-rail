package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;
import fi.livi.rata.avoindata.server.controller.utils.HttpUtils;

import javax.servlet.http.HttpServletRequest;

public class UnknownParametersException extends AbstractException {

    public UnknownParametersException(final HttpServletRequest request, final String parameterName) {
        super(String.format("Unknown parameter: %s, Url: %s", parameterName, HttpUtils.getFullURL(request)));
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.UNKNOWN_PARAMETER;
    }
}
