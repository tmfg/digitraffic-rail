package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

/**
 * Orchestrates NeTEx generation: fetches data, invokes sub-services, persists
 * the result.
 * Analogous to GTFSService.
 */
@Service
public class NeTExService {

    private static final Logger log = LoggerFactory.getLogger(NeTExService.class);
    private static final String NETEX_FILENAME = "netex-nordic-timetables.zip";
    private static final Set<String> EXCLUDED_TYPES = Set.of("V", "HV", "MV", "MUS");

    @Value("${updater.netex.peti.min-match-rate:0.95}")
    private double minMatchRate = 0.95;

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

            final NeTExGenerationResult result = generateNeTEx(adhocSchedules, regularSchedules, stations);

            final long durationMs = System.currentTimeMillis() - startTime;

            if (result != null) {
                final GeneratedExport export = new GeneratedExport();
                export.data = result.zip();
                export.created = ZonedDateTime.now();
                export.fileName = NETEX_FILENAME;
                generatedExportRepository.persist(List.of(export));

                final int petiTotal = result.matchedCount() + result.unmatchedCount();
                final double matchRate = petiTotal > 0 ? (double) result.matchedCount() / petiTotal : 0.0;
                log.info("event=rail.netex.generation outcome=success duration_ms={} "
                        + "scheduled_stop_points={} routes={} lines={} service_journeys={} "
                        + "peti_stations_total={} peti_stations_matched={} peti_stations_unmatched={} "
                        + "peti_match_rate={} "
                        + "quay_matched_count={} quay_unmatched_count={} quay_no_track_count={}",
                        durationMs,
                        result.scheduledStopPoints(), result.routes(), result.lines(), result.serviceJourneys(),
                        petiTotal, result.matchedCount(), result.unmatchedCount(),
                        String.format("%.4f", matchRate),
                        result.quayMatchedCount(), result.quayUnmatchedCount(), result.quayNoTrackCount());
            } else {
                log.info("event=rail.netex.generation outcome=no_data duration_ms={}", durationMs);
            }
        } catch (final Exception e) {
            final long durationMs = System.currentTimeMillis() - startTime;
            log.info("event=rail.netex.generation outcome=failed duration_ms={}", durationMs);
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
     * @return generation result with ZIP and telemetry counts, or null if no schedules match
     */
    public NeTExGenerationResult generateNeTEx(final List<Schedule> adhocSchedules,
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

        final List<NeTExStopsService.StationTrackPair> trackPairs = extractStationTrackPairs(allFiltered);
        final NeTExStopsData stopsData = stopsService.createStopsData(stations, trackPairs);

        // Min-match-rate guard: only enforced when PETI source is non-empty
        final int total = stopsData.matchedCount() + stopsData.unmatchedCount();
        if (total > 0) {
            final double rate = (double) stopsData.matchedCount() / total;
            if (rate < minMatchRate) {
                throw new IllegalStateException(
                        "PETI match rate %.2f below threshold %.2f".formatted(rate, minMatchRate));
            }
        }

        final NeTExCalendarData calendarData = calendarService.createCalendarData(allFiltered);
        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(allFiltered);

        final var lines = entityService.createLines(allFiltered);
        final var operators = entityService.createOperators(allFiltered);
        final var serviceJourneys = entityService.createServiceJourneys(allFiltered, calendarData, routeData);

        final byte[] zip = writingService.writeNeTExZip(stopsData, routeData, calendarData, lines, operators,
                serviceJourneys, ZonedDateTime.now());
        return new NeTExGenerationResult(zip,
                stopsData.getScheduledStopPoints().size(), routeData.getRoutes().size(),
                lines.size(), serviceJourneys.size(),
                stopsData.matchedCount(), stopsData.unmatchedCount(),
                stopsData.quayMatchedCount(), stopsData.quayUnmatchedCount(), stopsData.quayNoTrackCount());
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
     * Resolves the NeTEx ServiceJourney id in effect for each (trainNumber, date)
     * within the given range, using the exact same passenger filter and
     * winning-schedule resolution as the timetable generation. Compositions use
     * this so their ServiceJourneyRefs point at ids that actually exist in the
     * timetable ServiceJourney set.
     */
    public Map<TrainId, String> resolveServiceJourneyIds(final List<Schedule> adhocSchedules,
            final List<Schedule> regularSchedules,
            final LocalDate start,
            final LocalDate end) {
        final List<Schedule> passengerAdhoc = filterPassengerTrains(adhocSchedules);
        final List<Schedule> passengerRegular = filterPassengerTrains(regularSchedules);

        final Map<TrainId, String> result = new HashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final List<Schedule> todaysSchedules = todaysScheduleService.getDaysSchedules(date, passengerAdhoc,
                    passengerRegular);
            for (final Schedule schedule : todaysSchedules) {
                if (!schedule.changeType.equals("P") && schedule.isRunOnDay(date)) {
                    result.put(new TrainId(schedule.trainNumber, date), entityService.serviceJourneyIdFor(schedule));
                }
            }
        }

        return result;
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

    /**
     * Extracts unique (stationShortCode, commercialTrack) pairs from schedule rows.
     */
    private List<NeTExStopsService.StationTrackPair> extractStationTrackPairs(final List<Schedule> schedules) {
        final var seen = new LinkedHashSet<NeTExStopsService.StationTrackPair>();
        for (final Schedule schedule : schedules) {
            for (final ScheduleRow row : schedule.scheduleRows) {
                seen.add(new NeTExStopsService.StationTrackPair(
                        row.station.stationShortCode, row.commercialTrack));
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * Holds the generation output and telemetry counts for the wide-event.
     */
    record NeTExGenerationResult(byte[] zip, int scheduledStopPoints, int routes, int lines,
                                  int serviceJourneys, int matchedCount, int unmatchedCount,
                                  int quayMatchedCount, int quayUnmatchedCount, int quayNoTrackCount) {
    }
}
