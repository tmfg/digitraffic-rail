package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

/**
 * Builds the NeTEx timetable delivery from schedule + station data. Packaging
 * of
 * the combined dataset ZIP and persistence is handled by
 * {@link NeTExPackageService}.
 */
@Service
public class NeTExService {

    private static final Logger log = LoggerFactory.getLogger(NeTExService.class);
    private static final Set<String> EXCLUDED_TYPES = Set.of("V", "HV", "MV");

    @Value("${updater.netex.peti.min-match-rate:0.95}")
    private double minMatchRate = 0.95;

    private final NeTExEntityService entityService;
    private final NeTExCalendarService calendarService;
    private final NeTExRouteService routeService;
    private final NeTExStopsService stopsService;
    private final NeTExWritingService writingService;
    private final PetiStopSource petiStopSource;
    private final ScheduleProviderService scheduleProviderService;
    private final TodaysScheduleService todaysScheduleService;
    private final StationRepository stationRepository;

    public NeTExService(final NeTExEntityService entityService,
            final NeTExCalendarService calendarService,
            final NeTExRouteService routeService,
            final NeTExStopsService stopsService,
            final NeTExWritingService writingService,
            final PetiStopSource petiStopSource,
            final ScheduleProviderService scheduleProviderService,
            final TodaysScheduleService todaysScheduleService,
            final StationRepository stationRepository) {
        this.entityService = entityService;
        this.calendarService = calendarService;
        this.routeService = routeService;
        this.stopsService = stopsService;
        this.writingService = writingService;
        this.petiStopSource = petiStopSource;
        this.scheduleProviderService = scheduleProviderService;
        this.todaysScheduleService = todaysScheduleService;
        this.stationRepository = stationRepository;
    }

    /**
     * Fetches schedule and station data and builds the timetable delivery, logging
     * the generation wide-event. Returns the built package (or null when there is
     * no
     * data); persistence and packaging into the combined ZIP is done by
     * {@link NeTExPackageService}.
     */
    @Transactional
    public NeTExGenerationResult generateNeTEx() {
        log.info("method=generateNeTEx starting NeTEx generation");
        final long startTime = System.currentTimeMillis();
        Stage stage = Stage.FETCH;

        try {
            final LocalDate start = feedStart();
            final List<Schedule> adhocSchedules = scheduleProviderService.getAdhocSchedules(start);
            final List<Schedule> regularSchedules = scheduleProviderService.getRegularSchedules(start);
            final List<Station> stations = stationRepository.findAll();

            log.info("method=generateNeTEx fetched data adhocSchedules={} regularSchedules={} stations={}",
                    adhocSchedules.size(), regularSchedules.size(), stations.size());

            stage = Stage.GENERATE;
            final NeTExGenerationResult result = generateNeTEx(adhocSchedules, regularSchedules, stations);

            final long durationMs = System.currentTimeMillis() - startTime;
            logGenerationEvent(result != null ? "success" : "no_data", "NULL", Stage.COMPLETE, durationMs, result);
            return result;
        } catch (final Exception e) {
            final long durationMs = System.currentTimeMillis() - startTime;
            logGenerationEvent("error", e.getClass().getSimpleName(), stage, durationMs, null);
            log.error("method=generateNeTEx failed, durationMs={}", durationMs, e);
            throw new RuntimeException("NeTEx generation failed", e);
        }
    }

    /** How far a generation cycle got — emitted as {@code stage=…} so an error line says where it failed. */
    private enum Stage { FETCH, GENERATE, COMPLETE }

    /**
     * Emits the one-line {@code rail.netex.generation} wide event — same field set on every outcome (zeros /
     * NULL where unavailable); {@code error} is logged at ERROR (with {@code error.type} + {@code stage}),
     * everything else at INFO. All counts are {@code rail.netex.*}-namespaced to match the SIRI wide event.
     */
    private void logGenerationEvent(final String outcome, final String errorType, final Stage stage,
            final long durationMs, final NeTExGenerationResult result) {
        final int petiTotal = result != null ? result.matchedCount() + result.unmatchedCount() : 0;
        final double matchRate = petiTotal > 0 ? (double) result.matchedCount() / petiTotal : 0.0;
        final String line = StringUtil.format(
                "event=rail.netex.generation outcome={} error.type={} stage={} duration_ms={} "
                        + "rail.netex.scheduled_stop_points={} rail.netex.routes={} rail.netex.lines={} "
                        + "rail.netex.service_journeys={} rail.netex.peti.stop_assignments_total={} "
                        + "rail.netex.peti.stop_assignments_matched={} rail.netex.peti.stop_assignments_unmatched={} "
                        + "rail.netex.peti.match_rate={} rail.netex.peti.quay_matched_count={} "
                        + "rail.netex.peti.quay_unmatched_count={} rail.netex.peti.quay_no_track_count={}",
                outcome, errorType, stage.name().toLowerCase(Locale.ROOT), durationMs,
                result != null ? result.scheduledStopPoints() : 0,
                result != null ? result.routes() : 0,
                result != null ? result.lines() : 0,
                result != null ? result.serviceJourneys() : 0,
                petiTotal,
                result != null ? result.matchedCount() : 0,
                result != null ? result.unmatchedCount() : 0,
                String.format(Locale.ROOT, "%.4f", matchRate),
                result != null ? result.quayMatchedCount() : 0,
                result != null ? result.quayUnmatchedCount() : 0,
                result != null ? result.quayNoTrackCount() : 0);
        if ("error".equals(outcome)) {
            log.error(line);
        } else {
            log.info(line);
        }
    }

    /**
     * Generates the NeTEx Nordic ZIP from the given schedules and stations by chaining the three stages:
     * {@link #computeDataset} (the data), {@link #buildFiles} (the XML) and {@link #zip} (the archive).
     * Returns {@code null} when no schedules match.
     */
    public NeTExGenerationResult generateNeTEx(final List<Schedule> adhocSchedules,
            final List<Schedule> regularSchedules,
            final List<Station> stations) {
        final NeTExDataset dataset = computeDataset(adhocSchedules, regularSchedules, stations);
        if (dataset == null) {
            return null;
        }
        final Map<String, PublicationDeliveryStructure> files = buildFiles(dataset);
        final byte[] zip = zip(files);

        final NeTExStopsData stopsData = dataset.stopsData();
        return new NeTExGenerationResult(zip, files, dataset.operatingDays(),
                stopsData.getScheduledStopPoints().size(), dataset.routeData().getRoutes().size(),
                dataset.lines().size(), dataset.serviceJourneys().size(),
                stopsData.matchedCount(), stopsData.unmatchedCount(),
                stopsData.quayMatchedCount(), stopsData.quayUnmatchedCount(), stopsData.quayNoTrackCount(),
                dataset);
    }

    /**
     * Stage 1 — computes every NeTEx structure needed both to render the XML and to persist the resolved
     * journey refs: the winning passenger schedules (overall and per operating day), stops, routes, lines,
     * operators, service journeys and the calendar. Returns {@code null} when no schedules match; does no
     * XML/ZIP work.
     */
    public NeTExDataset computeDataset(final List<Schedule> adhocSchedules,
            final List<Schedule> regularSchedules,
            final List<Station> stations) {
        // Filter to passenger trains first (matches GTFS gtfs-passenger.zip approach)
        final Set<String> publishable = publishableStations(stations);
        final List<Schedule> passengerAdhoc = dropUnpublishableStops(filterPassengerTrains(adhocSchedules),
                publishable);
        final List<Schedule> passengerRegular = dropUnpublishableStops(filterPassengerTrains(regularSchedules),
                publishable);

        // Resolve which schedules are "in effect" among passenger trains only
        final Set<Long> winningScheduleIds = resolveWinningScheduleIds(passengerAdhoc, passengerRegular);

        log.info("method=computeDataset resolved winningScheduleIds={} from passengerAdhoc={} passengerRegular={}",
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

        // Load PETI on demand so generation never depends on the daily warm-up having
        // run first.
        // When the feed is unavailable, generation degrades (no stop assignments)
        // rather than failing.
        petiStopSource.ensureLoaded();
        final int petiStopPlaces = petiStopSource.getStops().size();
        final int petiQuays = petiStopSource.getStops().stream().mapToInt(s -> s.quays().size()).sum();
        log.info("method=computeDataset peti_fetch_outcome={} peti_stop_places={} peti_quays={}",
                petiStopPlaces > 0 ? "success" : "empty", petiStopPlaces, petiQuays);

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

        final NeTExRouteData routeData = routeService.createRouteDataTrackAware(allFiltered);

        final Map<String, String> stationNames = stations.stream()
                .collect(Collectors.toMap(station -> station.shortCode,
                        station -> NeTExStopsService.publicStationName(station.name),
                        (first, second) -> first));
        final var lines = entityService.createLines(allFiltered, routeData, stationNames);
        final var operators = entityService.createOperators(allFiltered);
        final var serviceJourneys = entityService.createServiceJourneys(allFiltered, routeData);

        // The winning schedule per (train, day) drives both the calendar and the persisted journey refs, so
        // DayTypes, ServiceJourneys and the published refs cannot disagree — resolved once here.
        final Map<TrainId, Schedule> winningByTrainDate = resolveWinningSchedules(adhocSchedules, regularSchedules,
                publishable, feedStart(), feedEnd());
        final Map<TrainId, String> datedRefs = new HashMap<>();
        winningByTrainDate.forEach((trainId, schedule) ->
                datedRefs.put(trainId, entityService.serviceJourneyIdFor(schedule)));
        final NeTExCalendarService.NeTExCalendarData calendar = calendarService.createCalendarData(datedRefs);
        final List<LocalDate> operatingDays = calendar.datesOf(
                serviceJourneys.stream().map(NeTExEntityService.NeTExServiceJourney::id).toList());

        log.info("method=computeDataset calendar dayTypes={} operatingPeriods={} dayTypeAssignments={}",
                calendar.dayTypes().size(), calendar.operatingPeriods().size(), calendar.assignments().size());

        final List<PublishedJourneyDraft> publishedJourneys = buildPublishedJourneyDrafts(winningByTrainDate,
                serviceJourneys);

        return new NeTExDataset(allFiltered, publishedJourneys, stopsData, routeData,
                lines, operators, serviceJourneys, calendar, operatingDays);
    }

    /**
     * Joins each winning {@code (train, date)} schedule to its built {@code ServiceJourney} once, producing the
     * per-journey drafts the writer persists. Doing the id-join here (rather than in the writer) keeps the
     * persisted shape — refs plus planned tracks — explicit at the point the data is computed.
     */
    private List<PublishedJourneyDraft> buildPublishedJourneyDrafts(final Map<TrainId, Schedule> winningByTrainDate,
            final List<NeTExEntityService.NeTExServiceJourney> serviceJourneys) {
        final Map<String, NeTExEntityService.NeTExServiceJourney> serviceJourneysById = serviceJourneys.stream()
                .collect(Collectors.toMap(NeTExEntityService.NeTExServiceJourney::id, sj -> sj, (a, b) -> a));

        final List<PublishedJourneyDraft> drafts = new ArrayList<>();
        winningByTrainDate.forEach((trainId, schedule) -> {
            final String serviceJourneyId = entityService.serviceJourneyIdFor(schedule);
            final NeTExEntityService.NeTExServiceJourney serviceJourney = serviceJourneysById.get(serviceJourneyId);
            if (serviceJourney == null) {
                return;
            }
            final List<PublishedJourneyDraft.PublishedTrack> tracks = serviceJourney.passingTimes().stream()
                    .filter(pt -> pt.commercialTrack() != null && pt.stationShortCode() != null)
                    .map(pt -> new PublishedJourneyDraft.PublishedTrack(pt.stationShortCode(), pt.commercialTrack()))
                    .toList();
            drafts.add(new PublishedJourneyDraft(trainId, serviceJourneyId, serviceJourney.lineRef(),
                    serviceJourney.operatorRef(), serviceJourney.journeyPatternRef(), tracks));
        });
        return drafts;
    }

    /** Stage 2 — renders the computed dataset into the per-Line NeTEx XML documents. */
    public Map<String, PublicationDeliveryStructure> buildFiles(final NeTExDataset dataset) {
        return writingService.buildDataset(dataset.stopsData(), dataset.routeData(), dataset.lines(),
                dataset.operators(), dataset.serviceJourneys(), dataset.calendar(), DateProvider.nowInHelsinki());
    }

    /** Stage 3 — marshals and zips the XML documents into the single distributable archive. */
    public byte[] zip(final Map<String, PublicationDeliveryStructure> files) {
        return writingService.marshalAndZip(files);
    }

    /**
     * The computed NeTEx data (stage 1 output): everything needed to render the XML <em>and</em> to persist the
     * resolved journey refs, without re-fetching or re-resolving. {@code publishedJourneys} is the per-journey
     * aggregate (refs + planned tracks) for every winning {@code (trainNumber, operatingDate)} across the feed
     * horizon — already joined so the writer just maps it to rows.
     */
    public record NeTExDataset(List<Schedule> winningSchedules,
            List<PublishedJourneyDraft> publishedJourneys,
            NeTExStopsData stopsData,
            NeTExRouteData routeData,
            List<NeTExEntityService.NeTExLine> lines,
            List<NeTExEntityService.NeTExOperator> operators,
            List<NeTExEntityService.NeTExServiceJourney> serviceJourneys,
            NeTExCalendarService.NeTExCalendarData calendar,
            List<LocalDate> operatingDays) {
    }

    /**
     * Schedules and dated journeys share one horizon so the two cannot drift apart.
     */
    private static LocalDate feedStart() {
        return DateProvider.dateInHelsinki().minusDays(7);
    }

    private static LocalDate feedEnd() {
        return feedStart().plusYears(1).withMonth(12).withDayOfMonth(31);
    }

    /**
     * Resolves which schedule IDs actually "win" for at least one day.
     * Uses TodaysScheduleService (same as GTFS) to determine the schedule in effect
     * per train per day, filtering out superseded/broken schedule versions.
     */
    private Set<Long> resolveWinningScheduleIds(final List<Schedule> adhocSchedules,
            final List<Schedule> regularSchedules) {
        final LocalDate start = feedStart();
        final LocalDate end = feedEnd();
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
     * winning-schedule resolution as the timetable generation.
     */
    public Map<TrainId, String> resolveServiceJourneyIds(final List<Schedule> adhocSchedules,
            final List<Schedule> regularSchedules,
            final Set<String> publishableStations,
            final LocalDate start,
            final LocalDate end) {
        final Map<TrainId, String> result = new HashMap<>();
        resolveWinningSchedules(adhocSchedules, regularSchedules, publishableStations, start, end)
                .forEach((trainId, schedule) -> result.put(trainId, entityService.serviceJourneyIdFor(schedule)));
        return result;
    }

    /**
     * Resolves the winning passenger {@link Schedule} in effect for each
     * (trainNumber, date) in the range, using the exact same passenger filter,
     * unpublishable-stop dropping and winning-schedule resolution as the timetable
     * generation. Real-time producers (SIRI) use this so their journey/line refs
     * point at journeys that actually exist in the published ServiceJourney set.
     */
    public Map<TrainId, Schedule> resolveWinningSchedules(final List<Schedule> adhocSchedules,
            final List<Schedule> regularSchedules,
            final Set<String> publishableStations,
            final LocalDate start,
            final LocalDate end) {
        final List<Schedule> passengerAdhoc = dropUnpublishableStops(filterPassengerTrains(adhocSchedules),
                publishableStations);
        final List<Schedule> passengerRegular = dropUnpublishableStops(filterPassengerTrains(regularSchedules),
                publishableStations);

        final Map<TrainId, Schedule> result = new HashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            final List<Schedule> todaysSchedules = todaysScheduleService.getDaysSchedules(date, passengerAdhoc,
                    passengerRegular);
            for (final Schedule schedule : todaysSchedules) {
                if (!schedule.changeType.equals("P") && schedule.isRunOnDay(date)) {
                    result.put(new TrainId(schedule.trainNumber, date), schedule);
                }
            }
        }

        return result;
    }

    /**
     * Stations we are able to declare as ScheduledStopPoints.
     */
    public static Set<String> publishableStations(final List<Station> stations) {
        return stations.stream()
                .filter(station -> station.passengerTraffic)
                .map(station -> station.shortCode)
                .collect(Collectors.toSet());
    }

    /**
     * Drops schedules that make a commercial stop at a station we cannot declare,
     * which would otherwise leave the journey pattern referencing a
     * ScheduledStopPoint that does not exist. Self-correcting: the schedules
     * return once the station metadata catches up.
     */
    private List<Schedule> dropUnpublishableStops(final List<Schedule> schedules,
            final Set<String> publishableStations) {
        final Map<String, Integer> droppedByStation = new TreeMap<>();
        final List<Schedule> kept = new ArrayList<>();

        for (final Schedule schedule : schedules) {
            final Set<String> missing = routeService.extractCommercialStopsWithTrack(schedule).stream()
                    .map(NeTExRouteService.StopWithTrack::stationShortCode)
                    .filter(code -> !publishableStations.contains(code))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (missing.isEmpty()) {
                kept.add(schedule);
            } else {
                missing.forEach(code -> droppedByStation.merge(code, 1, Integer::sum));
            }
        }

        if (!droppedByStation.isEmpty()) {
            // Self-correcting: the schedules return once the station metadata catches up, so this is a WARN
            // (visibility) rather than an ERROR (action required).
            log.warn("method=dropUnpublishableStops droppedSchedules={} stationsNotPublishable={}",
                    schedules.size() - kept.size(), droppedByStation);
        }
        return kept;
    }

    /** Mirrors GTFSService.isPassengerTrain so both feeds publish the same journeys. */
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
    record NeTExGenerationResult(byte[] zip,
            Map<String, PublicationDeliveryStructure> files, List<LocalDate> operatingDays,
            int scheduledStopPoints, int routes, int lines,
            int serviceJourneys, int matchedCount, int unmatchedCount,
            int quayMatchedCount, int quayUnmatchedCount, int quayNoTrackCount,
            NeTExDataset dataset) {
    }
}
