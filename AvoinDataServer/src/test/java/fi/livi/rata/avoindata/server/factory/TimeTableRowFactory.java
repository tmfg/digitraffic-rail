package fi.livi.rata.avoindata.server.factory;


import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;

@Component
public class TimeTableRowFactory {
    public long attapId = 1L;

    public TimeTableRow create(Train train, ZonedDateTime scheduledTime, final ZonedDateTime actualTime, StationEmbeddable station,
                               final TimeTableRow.TimeTableRowType timeTableRowType) {
        final String stationShortCode = station.stationShortCode;
        final int stationcUICCode = station.stationUICCode;
        final String countryCode = station.countryCode;
        final TimeTableRow.TimeTableRowType type = timeTableRowType;
        final String commercialTrack = "1";
        final boolean cancelled = false;
        final ZonedDateTime liveEstimateTime = null;

        final Long differenceInMinutes = null;
        final long atappiId = attapId++;

        final long trainNumber = train.id.trainNumber;
        final LocalDate departureDate = train.id.departureDate;
        final Boolean commercialStop = true;
        long version = 1L;
        final TimeTableRow timeTableRow = new TimeTableRow(stationShortCode, stationcUICCode, countryCode, type, commercialTrack, cancelled,
                scheduledTime, liveEstimateTime, actualTime, differenceInMinutes, atappiId, trainNumber, departureDate, commercialStop,
                version, null, null, null);

        train.timeTableRows.add(timeTableRow);
        timeTableRow.train = train;

        return timeTableRow;
    }
}
