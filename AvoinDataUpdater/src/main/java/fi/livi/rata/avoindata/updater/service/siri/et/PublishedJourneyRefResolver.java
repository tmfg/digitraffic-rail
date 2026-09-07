package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;

/**
 * {@link JourneyRefResolver} backed by NeTEx-published journey refs read from the DB, keyed per
 * {@code (trainNumber, departureDate)}. The DB read path uses this instead of re-resolving from RIPA.
 */
public class PublishedJourneyRefResolver implements JourneyRefResolver {

    private final Map<TrainId, ResolvedJourney> byTrainId;

    public PublishedJourneyRefResolver(final Map<TrainId, ResolvedJourney> byTrainId) {
        this.byTrainId = byTrainId;
    }

    @Override
    public Optional<ResolvedJourney> resolve(final long trainNumber, final LocalDate departureDate) {
        return Optional.ofNullable(byTrainId.get(new TrainId(trainNumber, departureDate)));
    }
}
