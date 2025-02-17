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

    int getSpeed();

    int getAccuracy();

    String getStationShortCode();

    String getCommercialTrack();

    static String createStopId(final String stationCode, final String commercialTrack) {
        if(stationCode == null) {
            return null;
        }

        // if commercialTrack is set, then create stopId as SHORTCODE_COMMERCIALTRACK
        // otherwise use SHORTCODE_0
        if(commercialTrack == null || commercialTrack.equals("")) {
            return String.format("%s_0", stationCode);
        }

        return String.format("%s_%s", stationCode, commercialTrack);
    }

}
