package fi.livi.rata.avoindata.common.domain.stopmonitoring;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public interface UdotData {
    int getAttapId();

    Boolean getUnknownDelay();

    Boolean getUnknownTrack();

    long getTrainNumber();

    LocalDate getTrainDepartureDate();

    ZonedDateTime getModifiedDb();
}
