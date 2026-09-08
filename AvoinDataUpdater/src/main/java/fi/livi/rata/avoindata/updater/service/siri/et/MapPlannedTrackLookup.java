package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

/**
 * {@link PlannedTrackLookup} backed by a {@code (trainNumber, departureDate) -> stationShortCode -> visitIndex
 * -> plannedTrack} map read from the DB. Keying by visit index (not station alone) keeps a distinct planned
 * track for each visit when a journey serves the same station more than once. The DB read path uses this
 * instead of re-resolving from RIPA.
 */
public class MapPlannedTrackLookup implements PlannedTrackLookup {

    private final Map<TrainId, Map<String, Map<Integer, String>>> byTrainId;

    public MapPlannedTrackLookup(final Map<TrainId, Map<String, Map<Integer, String>>> byTrainId) {
        this.byTrainId = byTrainId;
    }

    @Override
    public Optional<String> plannedTrack(final long trainNumber, final LocalDate departureDate,
                                         final String stationShortCode, final int visitIndex) {
        final Map<String, Map<Integer, String>> byStation = byTrainId.get(new TrainId(trainNumber, departureDate));
        if (byStation == null) {
            return Optional.empty();
        }
        final Map<Integer, String> byVisit = byStation.get(stationShortCode);
        if (byVisit == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byVisit.get(visitIndex));
    }
}
