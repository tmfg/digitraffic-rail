package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

import java.util.List;

/**
 * Builds core NeTEx domain objects: Lines, Operators, Authority, ServiceJourneys, Network.
 */
public class NeTExEntityService {

    private final NeTExIdGenerator idGenerator;
    private final NeTExTimeConverter timeConverter;

    public NeTExEntityService(final NeTExIdGenerator idGenerator, final NeTExTimeConverter timeConverter) {
        this.idGenerator = idGenerator;
        this.timeConverter = timeConverter;
    }

    /**
     * Derives the Line identifier for a schedule.
     * Commuter trains use commuterLineId, long-distance trains use trainType name.
     */
    public String deriveLineId(final Schedule schedule) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Creates the list of unique Lines from schedules.
     */
    public List<NeTExLine> createLines(final List<Schedule> schedules) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Creates the list of unique Operators from schedules.
     */
    public List<NeTExOperator> createOperators(final List<Schedule> schedules) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Creates ServiceJourneys from schedules with calendar and route references.
     */
    public List<NeTExServiceJourney> createServiceJourneys(final List<Schedule> schedules,
                                                            final NeTExCalendarData calendarData,
                                                            final NeTExRouteData routeData) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public record NeTExLine(String id, String publicCode, String transportMode) {}
    public record NeTExOperator(String id, String name, String privateCode, int companyNumber) {}
    public record NeTExServiceJourney(String id, String name, String privateCode,
                                       String journeyPatternRef, String operatorRef,
                                       String lineRef, String dayTypeRef,
                                       List<NeTExPassingTime> passingTimes) {}
    public record NeTExPassingTime(int order, String arrivalTime, String departureTime) {}
}
