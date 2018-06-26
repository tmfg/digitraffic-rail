package fi.livi.rata.avoindata.updater.factory;

import fi.livi.rata.avoindata.common.domain.train.Forecast;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import org.springframework.stereotype.Component;

@Component
public class ForecastFactory {
    public Forecast create(TimeTableRow timeTableRow, int difference) {
        Forecast forecast = new Forecast();
        forecast.timeTableRow = timeTableRow;
        forecast.source = "MIKUUSER";
        forecast.difference = difference;
        forecast.isLate = false;
        forecast.forecastTime = timeTableRow.scheduledTime.plusMinutes(difference);
        forecast.version=1L;

        return forecast;
    }
}
