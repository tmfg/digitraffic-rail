package fi.livi.rata.avoindata.updater.service.netex;

import java.util.List;
import java.util.Map;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

/**
 * Holds route and journey pattern data produced by NeTExRouteService.
 */
public class NeTExRouteData {

    private final List<NeTExRoute> routes;
    private final List<NeTExJourneyPattern> journeyPatterns;
    private final Map<String, String> scheduleToJourneyPatternId;

    public NeTExRouteData(
            final List<NeTExRoute> routes,
            final List<NeTExJourneyPattern> journeyPatterns,
            final Map<String, String> scheduleToJourneyPatternId) {
        this.routes = routes;
        this.journeyPatterns = journeyPatterns;
        this.scheduleToJourneyPatternId = scheduleToJourneyPatternId;
    }

    public List<NeTExRoute> getRoutes() {
        return routes;
    }

    public List<NeTExJourneyPattern> getJourneyPatterns() {
        return journeyPatterns;
    }

    public String getJourneyPatternIdForSchedule(final long scheduleId) {
        return scheduleToJourneyPatternId.get(String.valueOf(scheduleId));
    }

    public String getJourneyPatternIdForSchedule(final Schedule schedule) {
        return scheduleToJourneyPatternId.get(schedule.journeyKey());
    }

    public record NeTExRoute(String id, String name, String lineRef, List<String> routePointRefs) {
    }

    public record NeTExJourneyPattern(String id, String routeRef, List<NeTExStopPointInPattern> stopPoints) {
    }

    public record NeTExStopPointInPattern(int order, String scheduledStopPointRef, boolean forBoarding,
            boolean forAlighting, String destinationDisplayRef) {
    }
}
