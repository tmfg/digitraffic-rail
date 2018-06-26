package fi.livi.rata.avoindata.server.controller.api.exception;


import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

import java.time.LocalDate;

public class CompositionNotFoundException extends AbstractNotFoundException {
    public CompositionNotFoundException(long trainNumber, LocalDate departureDate) {
        super(String.format("Composition not found for %d on %s", trainNumber, departureDate));
    }

    public CompositionNotFoundException(LocalDate date) {
        super(String.format("Compositions not found for date %s", date));
    }

    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.COMPOSITION_NOT_FOUND;
    }
}
