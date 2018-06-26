package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

public class TooLongPeriodRequestedException extends AbstractException {
    public TooLongPeriodRequestedException(int day_limit, int numberOfDays) {
        super(String.format("The limit is %d and request was asking %d days.", day_limit, numberOfDays));
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.TOO_LONG_REQUESTED_TIME_PERIOD_FOR_SINGLE_QUERY;
    }
}
