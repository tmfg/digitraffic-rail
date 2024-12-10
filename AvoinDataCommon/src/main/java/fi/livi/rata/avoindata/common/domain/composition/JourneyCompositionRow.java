package fi.livi.rata.avoindata.common.domain.composition;

import java.time.ZonedDateTime;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

public class JourneyCompositionRow {
    public JourneyCompositionRow(final ZonedDateTime scheduledTime, final String stationShortCode, final int stationUICCode,
                                 final String countryCode, final TimeTableRow.TimeTableRowType type) {
        this.scheduledTime = scheduledTime;
        this.stationShortCode = stationShortCode;
        this.stationUICCode = stationUICCode;
        this.countryCode = countryCode;
        this.type = type;
    }

    public ZonedDateTime scheduledTime;
    public String stationShortCode;
    public int stationUICCode;
    public String countryCode;
    public TimeTableRow.TimeTableRowType type; // Departure/arrival

    @Override
    public String toString() {
        return "JourneyCompositionRow{" +
                "scheduledTime=" + scheduledTime +
                ", stationShortCode='" + stationShortCode + '\'' +
                ", stationUICCode=" + stationUICCode +
                ", countryCode='" + countryCode + '\'' +
                ", type=" + type +
                '}';
    }
}
