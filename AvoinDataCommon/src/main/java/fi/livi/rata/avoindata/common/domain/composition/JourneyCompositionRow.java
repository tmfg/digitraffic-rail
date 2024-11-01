package fi.livi.rata.avoindata.common.domain.composition;


import java.time.LocalDateTime;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

public class JourneyCompositionRow {
    public JourneyCompositionRow(final LocalDateTime scheduledTime, final String stationShortCode, final int stationUICCode,
            final String countryCode, final TimeTableRow.TimeTableRowType type) {
        this.scheduledTime = scheduledTime;
        this.stationShortCode = stationShortCode;
        this.stationUICCode = stationUICCode;
        this.countryCode = countryCode;
        this.type = type;
    }

    public LocalDateTime scheduledTime;
    public String stationShortCode;
    public int stationUICCode;
    public String countryCode;
    public TimeTableRow.TimeTableRowType type; // Departure/arrival
}
