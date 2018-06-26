package fi.livi.rata.avoindata.server.controller.api.exception;


import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

public class EndDateBeforeStartDateException extends AbstractException {
    public EndDateBeforeStartDateException(final String s) {
        super(s);
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.END_DATE_BEFORE_START_DATE;
    }
}
