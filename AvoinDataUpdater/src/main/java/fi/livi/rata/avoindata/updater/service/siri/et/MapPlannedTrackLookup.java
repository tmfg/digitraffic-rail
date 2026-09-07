package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

/**
 * {@link PlannedTrackLookup} backed by a {@code (trainNumber, departureDate) -> stationShortCode ->
 * plannedTrack} map read from the DB. The DB read path uses this instead of re-resolving from RIPA.
 */
public class MapPlannedTrackLookup implements PlannedTrackLookup {

    private final Map<TrainId, Map<String, String>> byTrainId;

    public MapPlannedTrackLookup(final Map<TrainId, Map<String, String>> byTrainId) {
        this.byTrainId = byTrainId;
    }

    @Override
    public Optional<String> plannedTrack(final long trainNumber, final LocalDate departureDate,
                                         final String stationShortCode) {
        final Map<String, String> byStation = byTrainId.get(new TrainId(trainNumber, departureDate));
        if (byStation == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byStation.get(stationShortCode));
    }
}
