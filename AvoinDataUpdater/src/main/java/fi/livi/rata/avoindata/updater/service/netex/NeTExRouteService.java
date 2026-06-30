package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

import java.util.List;

/**
 * Derives Routes and JourneyPatterns from schedule data.
 * Deduplicates patterns for trains sharing the same stop sequence.
 */
public class NeTExRouteService {

    private final NeTExIdGenerator idGenerator;

    public NeTExRouteService(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * Creates routes and journey patterns from the given schedules.
     * Trains with identical commercial stop sequences share a JourneyPattern.
     */
    public NeTExRouteData createRouteData(final List<Schedule> schedules) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Computes a deterministic hash from an ordered list of station short codes.
     */
    public String computeStopSequenceHash(final List<String> stationShortCodes) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
