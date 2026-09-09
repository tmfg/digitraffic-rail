package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolves the <em>planned</em> commercial track of a stop on a journey, so the interpreter can compare it
 * against the actual track and emit a SIRI {@code StopAssignment} on a genuine platform change. A seam kept
 * out of the timetable-entity layer; the generation side supplies {@link MapPlannedTrackLookup}. The
 * {@code visitIndex} (0-based occurrence of the station within the journey) disambiguates a station served more
 * than once.
 */
@FunctionalInterface
public interface PlannedTrackLookup {

    Optional<String> plannedTrack(long trainNumber, LocalDate departureDate, String stationShortCode, int visitIndex);
}
