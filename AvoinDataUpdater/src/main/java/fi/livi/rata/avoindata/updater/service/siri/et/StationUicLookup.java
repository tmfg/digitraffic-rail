package fi.livi.rata.avoindata.updater.service.siri.et;

import java.util.OptionalInt;

@FunctionalInterface
public interface StationUicLookup {
    OptionalInt uicFor(String stationShortCode);
}
