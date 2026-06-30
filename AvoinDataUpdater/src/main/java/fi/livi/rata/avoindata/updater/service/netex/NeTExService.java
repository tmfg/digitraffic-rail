package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates NeTEx generation: fetches data, invokes sub-services, persists the result.
 * Analogous to GTFSService.
 */
public class NeTExService {

    private static final Set<String> EXCLUDED_TYPES = Set.of("V", "HV", "MV", "MUS");

    private final NeTExEntityService entityService;
    private final NeTExCalendarService calendarService;
    private final NeTExRouteService routeService;
    private final NeTExStopsService stopsService;
    private final NeTExWritingService writingService;

    public NeTExService(final NeTExEntityService entityService,
                         final NeTExCalendarService calendarService,
                         final NeTExRouteService routeService,
                         final NeTExStopsService stopsService,
                         final NeTExWritingService writingService) {
        this.entityService = entityService;
        this.calendarService = calendarService;
        this.routeService = routeService;
        this.stopsService = stopsService;
        this.writingService = writingService;
    }

    /**
     * Generates NeTEx Nordic ZIP from the given schedules and stations.
     * Filters to passenger trains only, builds all NeTEx structures, writes ZIP.
     *
     * @return ZIP content as byte array, or null if no schedules match
     */
    public byte[] generateNeTEx(final List<Schedule> adhocSchedules,
                                 final List<Schedule> regularSchedules,
                                 final List<Station> stations) {
        final List<Schedule> allFiltered = new ArrayList<>();
        allFiltered.addAll(filterPassengerTrains(regularSchedules));
        allFiltered.addAll(filterPassengerTrains(adhocSchedules));

        if (allFiltered.isEmpty()) {
            return null;
        }

        final NeTExStopsData stopsData = stopsService.createStopsData(stations);
        final NeTExCalendarData calendarData = calendarService.createCalendarData(allFiltered);
        final NeTExRouteData routeData = routeService.createRouteData(allFiltered);

        final var lines = entityService.createLines(allFiltered);
        final var operators = entityService.createOperators(allFiltered);
        final var serviceJourneys = entityService.createServiceJourneys(allFiltered, calendarData, routeData);

        return writingService.writeNeTExZip(stopsData, routeData, calendarData, lines, operators, serviceJourneys,
                ZonedDateTime.now());
    }

    /**
     * Filters schedules to only include passenger trains.
     * Excludes: non-commercial, museum trains, types V/HV/MV.
     */
    public List<Schedule> filterPassengerTrains(final List<Schedule> schedules) {
        final List<Schedule> result = new ArrayList<>();
        for (final Schedule schedule : schedules) {
            if (schedule.trainType == null) {
                continue;
            }
            if (!schedule.trainType.commercial) {
                continue;
            }
            if (EXCLUDED_TYPES.contains(schedule.trainType.name)) {
                continue;
            }
            result.add(schedule);
        }
        return result;
    }
}
