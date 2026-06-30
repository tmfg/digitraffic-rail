package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

import java.util.List;

/**
 * Orchestrates NeTEx generation: fetches data, invokes sub-services, persists the result.
 * Analogous to GTFSService.
 */
public class NeTExService {

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
                                 final List<fi.livi.rata.avoindata.common.domain.metadata.Station> stations) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Filters schedules to only include passenger trains.
     * Excludes: non-commercial, museum trains, types V/HV/MV.
     */
    public List<Schedule> filterPassengerTrains(final List<Schedule> schedules) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
