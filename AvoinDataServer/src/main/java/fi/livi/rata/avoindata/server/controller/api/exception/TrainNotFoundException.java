package fi.livi.rata.avoindata.server.controller.api.exception;

import fi.livi.rata.avoindata.common.domain.common.ExceptionMessage;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public class TrainNotFoundException extends AbstractNotFoundException {
    public TrainNotFoundException(final Long trainNumber, final LocalDate departureDate) {
        super(String.format("Train number %d not found on %s", trainNumber, departureDate));
    }

    public TrainNotFoundException(final LocalDate date) {
        super(String.format("No trains found for %s", date));
    }

    public TrainNotFoundException(final String arrival_station, final String departure_station, final LocalDate departure_date) {
        super(String
                .format("No trains found for route from %s to %s for date %s. IMPORTANT! See the documentation for more " + "info on " +
                        "route queries.",
                        arrival_station, departure_station, departure_date));
    }

    public TrainNotFoundException(String arrival_station, String departure_station, ZonedDateTime from, ZonedDateTime to, Integer limit) {
        super(String
                .format("No trains found for route from %s to %s between %s and %s, limit %s", arrival_station, departure_station, from, to,
                        limit));
    }


    @Override
    public ExceptionMessage.ErrorCodeEnum getCode() {
        return ExceptionMessage.ErrorCodeEnum.TRAIN_NOT_FOUND;
    }
}
