package fi.livi.rata.avoindata.updater.service.siri.et;

import java.util.Optional;

/**
 * Resolves a station short code to its human-readable name for the SIRI {@code OriginName} /
 * {@code DestinationName} / {@code StopPointName} fields. A seam so the interpreter stays free of the DB;
 * the generation side supplies {@link InMemoryStationNameLookup}.
 */
@FunctionalInterface
public interface StationNameLookup {

    Optional<String> nameFor(String stationShortCode);
}
