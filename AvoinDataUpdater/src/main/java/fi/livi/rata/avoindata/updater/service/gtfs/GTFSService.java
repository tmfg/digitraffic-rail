package fi.livi.rata.avoindata.updater.service.gtfs;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiMapResult;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.common.utils.TimingUtil;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

@Service
public class GTFSService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public static int FIRST_STOP_SEQUENCE = 1;

    private final GTFSEntityService gtfsEntityService;
    private final GTFSWritingService gtfsWritingService;
    private final ScheduleProviderService scheduleProviderService;
    private final LastUpdateService lastUpdateService;
    private final GTFSTripService gtfsTripService;
    private final TrakediaLiikennepaikkaService trakediaLiikennepaikkaService;

    public GTFSService(final GTFSEntityService gtfsEntityService, final GTFSWritingService gtfsWritingService, final ScheduleProviderService scheduleProviderService, final LastUpdateService lastUpdateService, final GTFSTripService gtfsTripService, final TrakediaLiikennepaikkaService trakediaLiikennepaikkaService) {
        this.gtfsEntityService = gtfsEntityService;
        this.gtfsWritingService = gtfsWritingService;
        this.scheduleProviderService = scheduleProviderService;
        this.lastUpdateService = lastUpdateService;
        this.gtfsTripService = gtfsTripService;
        this.trakediaLiikennepaikkaService = trakediaLiikennepaikkaService;
    }

    // Evicted before the run, not after: the run must build on fresh geometry, and a run that
    // throws would otherwise leave the previous generation resident for the life of the task.
    @CacheEvict(cacheNames = { TrakediaRouteService.CACHE_NAME, InfraApiPlatformService.CACHE_NAME },
            allEntries = true, beforeInvocation = true)
    @Scheduled(cron = "${updater.gtfs.cron}", zone = "UTC")
    public void generateGTFS() {
        TimingUtil.log(log, "generateGTFS", () -> {
            final GtfsRunMetrics metrics = new GtfsRunMetrics(Clock.systemUTC());
            GtfsRunScope.run(metrics, () -> {
                try {
                    final LocalDate start = DateProvider.dateInHelsinki().minusDays(7);
                    this.generateGTFS(scheduleProviderService.getAdhocSchedules(start),
                            scheduleProviderService.getRegularSchedules(start), metrics);

                    lastUpdateService.update(LastUpdateService.LastUpdatedType.GTFS);
                } catch (final ExecutionException | InterruptedException | IOException | RuntimeException e) {
                    metrics.markError(e);
                    log.error("method=generateGTFS Error generating gtfs", e);

                    throw new RuntimeException(e);
                } finally {
                    logRunEvent(metrics);
                }
            });
        });
    }

    /** Emitted with an identical field set on every path; only the level differs. */
    private void logRunEvent(final GtfsRunMetrics metrics) {
        try {
            final String event = GtfsRunMetrics.toLogFields(metrics.finalEvent());
            if (metrics.outcome() == GtfsOutcome.SUCCESS) {
                log.info("{}", event);
            } else {
                log.error("{}", event);
            }
        } catch (final RuntimeException e) {
            // Must never mask the generation failure that is already propagating.
            log.error("method=logRunEvent Could not emit GTFS run event", e);
        }
    }

    //For generating test json
//    @PostConstruct
//    public void writeJson() throws ExecutionException, InterruptedException, IOException {
//        List<Schedule> allSchedules = new ArrayList<>();
//        allSchedules.addAll(scheduleProviderService.getAdhocSchedules(LocalDate.now()));
//        allSchedules.addAll(scheduleProviderService.getRegularSchedules(LocalDate.now()));
//
//        Set<Long> trainNumbers = Sets.newHashSet(141L,151L);
//        List<Schedule> filteredSchedules = allSchedules.stream().filter(schedule -> trainNumbers.contains( schedule.trainNumber)).collect(Collectors.toList());
//
//        log.info("Ids {}",filteredSchedules.stream().map(s->s.id).collect(Collectors.toList()));
//    }

    /**
     * Include stops that are set for passenger traffic and
     * stops that are used in the included stop times
     */
    private List<Stop> filterStops(final GTFSDto gtfsDto, final Set<String> stopIds) {
        return gtfsDto.stops.stream().filter(
                s -> BooleanUtils.isTrue(s.source.passengerTraffic) || stopIds.contains(s.stopId)
                ).toList();
    }

    private boolean hasAtLeastTwoStops(final Trip trip) {
        return trip.stopTimes.size() >= 2;
    }

    private List<Trip> filterOutTripsWithLessThanTwoStops(final GTFSDto gtfsDto) {
        return gtfsDto.trips.stream()
                .filter(this::hasAtLeastTwoStops)
                .toList();
    }

    private Set<String> collectStopIds(final List<Trip> trips) {
        return trips.stream()
                .flatMap(trip -> trip.stopTimes.stream())
                .flatMap(stopTime -> Stream.of(stopTime.getStopCodeWithPlatform(), stopTime.stopId))
                .collect(Collectors.toSet());
    }

    public GTFSDto createGtfs(final List<Schedule> passengerAdhocSchedules,
                              final List<Schedule> passengerRegularSchedules,
                              final String zipFileName,
                              final boolean filterOutNonStopsAndMuseumTrains) throws IOException {
        final GtfsRunMetrics metrics = new GtfsRunMetrics(Clock.systemUTC());
        return GtfsRunScope.call(metrics, () -> {
            try {
                return createGtfs(passengerAdhocSchedules, passengerRegularSchedules, zipFileName,
                        filterOutNonStopsAndMuseumTrains, resolveNodes(metrics), runDate(), new FailedSegments());
            } catch (final IOException | RuntimeException e) {
                metrics.markError(e);
                throw e;
            } finally {
                logRunEvent(metrics);
            }
        });
    }

    private GTFSDto createGtfs(final List<Schedule> passengerAdhocSchedules,
                               final List<Schedule> passengerRegularSchedules,
                               final String zipFileName,
                               final boolean filterOutNonStopsAndMuseumTrains,
                               final Map<String, JsonNode> nodes,
                               final LocalDate routeDate,
                               final FailedSegments failedSegments) throws IOException {
        final FeedMetricsSink metrics = GtfsRunScope.feedMetrics();
        metrics.recordFeedAttempt(zipFileName);
        try {
            // filter out museum trains when desired
            final var adhocSchedules = filterOutNonStopsAndMuseumTrains ? passengerAdhocSchedules.stream()
                    .filter(s -> !s.trainType.name.equals("MUS")).toList() : passengerAdhocSchedules;

            final GTFSDto gtfsDto = gtfsEntityService.createGTFSEntity(adhocSchedules, passengerRegularSchedules, nodes,
                    routeDate, failedSegments);

            if (filterOutNonStopsAndMuseumTrains) {
                for (final Trip trip : gtfsDto.trips) {
                    trip.stopTimes = this.filterOutNonStops(trip.stopTimes);
                }

                // first filter out invalid trips
                gtfsDto.trips = filterOutTripsWithLessThanTwoStops(gtfsDto);
                // and then filter the stop-ids
                final Set<String> stopIds = collectStopIds(gtfsDto.trips);
                gtfsDto.stops = filterStops(gtfsDto, stopIds);
            }

            gtfsWritingService.writeGTFSFiles(gtfsDto, zipFileName);
            metrics.recordFeedPublished(zipFileName);

            return gtfsDto;
        } catch (final IOException | RuntimeException e) {
            metrics.recordFeedFailed(zipFileName);
            throw e;
        }
    }

    public void generateGTFS(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules) throws IOException {
        final GtfsRunMetrics metrics = new GtfsRunMetrics(Clock.systemUTC());
        GtfsRunScope.call(metrics, () -> {
            try {
                generateGTFS(adhocSchedules, regularSchedules, metrics);
                return null;
            } catch (final IOException | RuntimeException e) {
                metrics.markError(e);
                throw e;
            } finally {
                logRunEvent(metrics);
            }
        });
    }

    /** Fixed for the run so every feed queries the same infrastructure validity date. */
    private static LocalDate runDate() {
        return LocalDate.now(Clock.systemUTC());
    }

    /**
     * Resolves one validated node snapshot for the whole run, so that all feeds are built from the
     * same source generation instead of re-reading an ambient, arbitrarily aged cache per feed.
     */
    private Map<String, JsonNode> resolveNodes(final GtfsRunMetrics metrics) {
        final InfraApiMapResult<JsonNode> nodeMap = trakediaLiikennepaikkaService.getTrakediaLiikennepaikkaNodes();
        metrics.recordNodeMap(nodeMap);
        return nodeMap.requireComplete();
    }

    private void generateGTFS(final List<Schedule> adhocSchedules, final List<Schedule> regularSchedules,
                              final GtfsRunMetrics metrics) throws IOException {
        final LocalDate routeDate = runDate();
        final FailedSegments failedSegments = new FailedSegments();
        final Map<String, JsonNode> nodes = resolveNodes(metrics);
        final GTFSDto gtfs = this.createGtfs(adhocSchedules, regularSchedules, "gtfs-all.zip", false, nodes, routeDate,
                failedSegments);

        final List<Schedule> passengerAdhocSchedules = adhocSchedules.stream().filter(this::isPassengerTrain).toList();
        final List<Schedule> passengerRegularSchedules = regularSchedules.stream().filter(this::isPassengerTrain).toList();

        this.createGtfs(passengerAdhocSchedules, passengerRegularSchedules, "gtfs-passenger.zip", false, nodes, routeDate, failedSegments);
        this.createGtfs(passengerAdhocSchedules, passengerRegularSchedules, "gtfs-passenger-stops.zip", true, nodes, routeDate, failedSegments);
        createVrGtfs(passengerAdhocSchedules, passengerRegularSchedules, nodes, routeDate, failedSegments);

        gtfsTripService.updateGtfsTrips(gtfs);

        log.info("Successfully wrote GTFS files");
    }

    private List<StopTime> filterOutNonStops(final List<StopTime> stopTimes) {
        final List<StopTime> filteredStopTimes = new ArrayList<>();

        int stopSequence = FIRST_STOP_SEQUENCE;
        for (final StopTime stopTime : stopTimes) {
            if (isLongStop(stopTime)) {
                filteredStopTimes.add(stopTime);
                stopTime.stopSequence = stopSequence++;
            }
        }

        return filteredStopTimes;
    }

    /**
     * Stop is a long stop, when
     * 1) it has no arrival or no departure
     * 2) arrival time differs from departure time and either on is commercial
     */
    private boolean isLongStop(final StopTime stopTime) {
        final ScheduleRow scheduleRow = stopTime.source;

        if(scheduleRow.departure == null || scheduleRow.arrival == null) {
            return true;
        }

        return (!stopTime.departureTime.equals(stopTime.arrivalTime) &&
                (scheduleRow.arrival.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL || scheduleRow.departure.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL));
    }

    private void createVrGtfs(final List<Schedule> passengerAdhocSchedules, final List<Schedule> passengerRegularSchedules,
                              final Map<String, JsonNode> nodes, final LocalDate routeDate,
                              final FailedSegments failedSegments) throws IOException {
        final List<Schedule> vrPassengerAdhocSchedules = createVrSchedules(passengerAdhocSchedules);
        final List<Schedule> vrPassengerRegularSchedules = createVrSchedules(passengerRegularSchedules);

        createGtfs(vrPassengerAdhocSchedules, vrPassengerRegularSchedules, "gtfs-vr.zip", true, nodes, routeDate, failedSegments);
        createVRTreGtfs(vrPassengerAdhocSchedules, vrPassengerRegularSchedules, nodes, routeDate, failedSegments);
    }

    private List<Schedule> createVrSchedules(final List<Schedule> passengerAdhocSchedules) {
        final Set<String> acceptedCommuterLineIds = Sets.newHashSet("R", "M", "T", "D", "G", "Z", "O", "H");
        final List<Schedule> vrPassengerAdhocSchedules = new ArrayList<>();
        for (final Schedule schedule : passengerAdhocSchedules) {
            if (schedule.operator.operatorUICCode == 10 &&
                    (Strings.isNullOrEmpty(schedule.commuterLineId) || acceptedCommuterLineIds.contains(schedule.commuterLineId))) {
                vrPassengerAdhocSchedules.add(schedule);
            }
        }
        return vrPassengerAdhocSchedules;
    }


    public void createVRTreGtfs(final List<Schedule> passengerAdhocSchedules, final List<Schedule> passengerRegularSchedules,
                                final Map<String, JsonNode> nodes, final LocalDate routeDate,
                                final FailedSegments failedSegments) throws IOException {
        final Set<String> includedStations = Sets.newHashSet("OV", "OVK", "LP\u00c4", "NOA");
        final Predicate<Schedule> treFilter = schedule -> schedule.scheduleRows.stream().anyMatch(scheduleRow -> includedStations.contains(scheduleRow.station.stationShortCode));
        final List<Schedule> vrTrePassengerAdhocSchedules = passengerAdhocSchedules.stream().filter(treFilter).collect(Collectors.toList());
        final List<Schedule> vrTreRegularSchedules = passengerRegularSchedules.stream().filter(treFilter).collect(Collectors.toList());

        createGtfs(vrTrePassengerAdhocSchedules, vrTreRegularSchedules, "gtfs-vr-tre.zip", true, nodes, routeDate, failedSegments);
    }

    private boolean isPassengerTrain(final Schedule s) {
        return (s.trainCategory.name.equals("Commuter") || (s.trainCategory.name.equals("Long-distance") && s.trainType.commercial)) && !Sets.newHashSet("V", "HV", "MV").contains(s.trainType.name);
    }
}
