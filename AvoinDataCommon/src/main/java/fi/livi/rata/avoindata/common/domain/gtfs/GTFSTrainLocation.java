package fi.livi.rata.avoindata.common.domain.gtfs;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public interface GTFSTrainLocation {
    long getId();

    LocalDate getDepartureDate();

    long getTrainNumber();

    ZonedDateTime getTimestamp();

    double getX();

    double getY();

    ///  this is km/h
    int getSpeed();

    int getAccuracy();

    String getStationShortCode();

    String getCommercialTrack();

    Boolean getUnknownTrack();
}
