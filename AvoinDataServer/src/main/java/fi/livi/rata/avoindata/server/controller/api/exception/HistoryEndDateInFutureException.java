package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

import java.time.LocalDate;

public class HistoryEndDateInFutureException extends AbstractException {
    public HistoryEndDateInFutureException(LocalDate endDate) {
        super(String.format("The end date is in future. End date was %s.", endDate));
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.END_DATE_IN_FUTURE_FOR_HISTORY_QUERY;
    }
}
