package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.common.utils.DateProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates NeTEx generation: fetches data, invokes sub-services, persists
 * the result.
 * Analogous to GTFSService.
 */
@Service
public class NeTExService {

    private static final Logger log = LoggerFactory.getLogger(NeTExService.class);
    private static final String NETEX_FILENAME = "netex-nordic.zip";
    private static final Set<String> EXCLUDED_TYPES = Set.of("V", "HV", "MV", "MUS");

    private final NeTExEntityService entityService;
    private final NeTExCalendarService calendarService;
    private final NeTExRouteService routeService;
    private final NeTExStopsService stopsService;
    private final NeTExWritingService writingService;
    private final ScheduleProviderService scheduleProviderService;
    private final TodaysScheduleService todaysScheduleService;
    private final StationRepository stationRepository;
    private final GeneratedExportRepository generatedExportRepository;

    public NeTExService(final NeTExEntityService entityService,
            final NeTExCalendarService calendarService,
            final NeTExRouteService routeService,
            final NeTExStopsService stopsService,
            final NeTExWritingService writingService,
            final ScheduleProviderService scheduleProviderService,
            final TodaysScheduleService todaysScheduleService,
            final StationRepository stationRepository,
            final GeneratedExportRepository generatedExportRepository) {
        this.entityService = entityService;
        this.calendarService = calendarService;
        this.routeService = routeService;
        this.stopsService = stopsService;
        this.writingService = writingService;
        this.scheduleProviderService = scheduleProviderService;
        this.todaysScheduleService = todaysScheduleService;
        this.stationRepository = stationRepository;
        this.generatedExportRepository = generatedExportRepository;
    }

    /**
     * Fetches schedule and station data, generates NeTEx Nordic ZIP, and persists
     * it.
     * Called manually via ManualUpdateController.
     */
    @Transactional
    public void generateNeTEx() {
        log.info("method=generateNeTEx starting NeTEx generation");
        final long startTime = System.currentTimeMillis();

        try {
            final LocalDate start = DateProvider.dateInHelsinki().minusDays(7);
            final List<Schedule> adhocSchedules = scheduleProviderService.getAdhocSchedules(start);
            final List<Schedule> regularSchedules = scheduleProviderService.getRegularSchedules(start);
            final List<Station> stations = stationRepository.findAll();

            log.info("method=generateNeTEx fetched data adhocSchedules={} regularSchedules={} stations={}",
                    adhocSchedules.size(), regularSchedules.size(), stations.size());

            final byte[] zip = generateNeTEx(adhocSchedules, regularSchedules, stations);

            final long durationMs = System.currentTimeMillis() - startTime;

            if (zip != null) {
                final GeneratedExport export = new GeneratedExport();
                export.data = zip;
                export.created = ZonedDateTime.now();
                export.fileName = NETEX_FILENAME;
                generatedExportRepository.persist(List.of(export));
                log.info("method=generateNeTEx persisted NeTEx ZIP, size={} bytes, durationMs={}", zip.length,
                        durationMs);
            } else {
                log.warn("method=generateNeTEx no passenger schedules found, nothing persisted, durationMs={}",
                        durationMs);
            }
        } catch (final Exception e) {
            final long durationMs = System.currentTimeMillis() - startTime;
            log.error("method=generateNeTEx failed, durationMs={}", durationMs, e);
            throw new RuntimeException("NeTEx generation failed", e);
        }
    }

    /**
     * Generates NeTEx Nordic ZIP from the given schedules and stations.
     * Filters to passenger trains first, then resolves winning schedules per
     * train per day (same as GTFS gtfs-passenger.zip), builds all NeTEx
     * structures, writes ZIP.
     *
     * @return ZIP content as byte array, or null if no schedules match
     */
    public byte[] generateNeTEx(final List<Schedule> adhocSchedules,
            final List<Schedule> regularSchedules,
            final List<Station> stations) {
        // Filter to passenger trains first (matches GTFS gtfs-passenger.zip approach)
        final List<Schedule> passengerAdhoc = filterPassengerTrains(adhocSchedules);
        final List<Schedule> passengerRegular = filterPassengerTrains(regularSchedules);

        // Resolve which schedules are "in effect" among passenger trains only
        final Set<Long> winningScheduleIds = resolveWinningScheduleIds(passengerAdhoc, passengerRegular);

        log.info("method=generateNeTEx resolved winningScheduleIds={} from passengerAdhoc={} passengerRegular={}",
                winningScheduleIds.size(), passengerAdhoc.size(), passengerRegular.size());

        final List<Schedule> allFiltered = new ArrayList<>();
        allFiltered.addAll(passengerRegular.stream()
                .filter(s -> winningScheduleIds.contains(s.id))
                .toList());
        allFiltered.addAll(passengerAdhoc.stream()
                .filter(s -> winningScheduleIds.contains(s.id))
                .toList());

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
     * Resolves which schedule IDs actually "win" for at least one day.
     * Uses TodaysScheduleService (same as GTFS) to determine the schedule in effect
     * per train per day, filtering out superseded/broken schedule versions.
     */
    private Set<Long> resolveWinningScheduleIds(final List<Schedule> adhocSchedules,
            final List<Schedule> regularSchedules) {
        final LocalDate start = DateProvider.dateInHelsinki().minusDays(7);
        final LocalDate end = start.plusYears(1).withMonth(12).withDayOfMonth(31);
        final Set<Long> winningIds = new HashSet<>();

        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            final List<Schedule> todaysSchedules = todaysScheduleService.getDaysSchedules(date, adhocSchedules,
                    regularSchedules);
            for (final Schedule schedule : todaysSchedules) {
                if (!schedule.changeType.equals("P") && schedule.isRunOnDay(date)) {
                    winningIds.add(schedule.id);
                }
            }
        }

        return winningIds;
    }

    /**
     * Filters schedules to only include passenger trains.
     * Matches GTFS logic: commuter trains, or commercial long-distance trains.
     * Excludes: cargo, museum trains, types V/HV/MV/MUS.
     */
    public List<Schedule> filterPassengerTrains(final List<Schedule> schedules) {
        final List<Schedule> result = new ArrayList<>();
        for (final Schedule schedule : schedules) {
            if (schedule.trainType == null || schedule.trainCategory == null) {
                continue;
            }
            final String category = schedule.trainCategory.name;
            if (!("Commuter".equals(category) || ("Long-distance".equals(category) && schedule.trainType.commercial))) {
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
