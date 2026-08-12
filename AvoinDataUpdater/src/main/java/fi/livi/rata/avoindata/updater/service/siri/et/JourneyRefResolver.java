package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.util.Optional;

import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;

@FunctionalInterface
public interface JourneyRefResolver {
    Optional<ResolvedJourney> resolve(long trainNumber, LocalDate departureDate);
}
