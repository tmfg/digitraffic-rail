package fi.livi.rata.avoindata.updater.service.siri.vm;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourneyTrack;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.observability.LogFields;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.netex.OperatingDayWindow;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.CommercialStopVisits;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.InvalidSiriOutputException;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyEndpoint;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyPatternRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.PetiUnavailableException;
import fi.livi.rata.avoindata.updater.service.siri.common.PublishedJourneysUnavailableException;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.common.TimeTableRowsLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.InMemoryStationNameLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.InMemoryStationUicLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.JourneyRefResolver;
import fi.livi.rata.avoindata.updater.service.siri.et.MapPlannedTrackLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.PlannedTrackLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.PublishedJourneyRefResolver;

import static fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository.ANY_SOURCE_VERSION;

/**
 * Generates the SIRI-VM feed on a schedule (see {@code SiriVmUpdatingService}) from the live {@code
 * train_location} table, following the same prepare/build/validate/persist pipeline as {@code
 * SiriEtGenerationService}. Reuses the same NeTEx-published-journey and PETI-quay lookups as SIRI-ET so both
 * feeds agree on line/journey/quay identifiers for a given train.
 */
@Service
public class SiriVmGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SiriVmGenerationService.class);

    // Same freshness window as the existing GTFS-Realtime VehiclePosition feed (FeedMessageService).
    private static final int LOCATION_MAX_AGE_MINUTES = 30;

    // Published journeys older than this (vs. their newest generatedAt) are treated as stale -> the cycle fails.
    private static final long JOURNEY_SOURCE_MAX_AGE_HOURS = 26;

    private final StationRepository stationRepository;
    private final TrainLocationRepository trainLocationRepository;
    private final GTFSTrainRepository gtfsTrainRepository;
    private final PetiStopSource petiStopSource;
    private final SiriWritingService siriWritingService;
    private final GeneratedExportRepository generatedExportRepository;
    private final NeTExPublishedJourneyRepository publishedJourneyRepository;

    public SiriVmGenerationService(
            final StationRepository stationRepository,
            final TrainLocationRepository trainLocationRepository,
            final GTFSTrainRepository gtfsTrainRepository,
            final PetiStopSource petiStopSource,
            final SiriWritingService siriWritingService,
            final GeneratedExportRepository generatedExportRepository,
            final NeTExPublishedJourneyRepository publishedJourneyRepository) {
        this.stationRepository = stationRepository;
        this.trainLocationRepository = trainLocationRepository;
        this.gtfsTrainRepository = gtfsTrainRepository;
        this.petiStopSource = petiStopSource;
        this.siriWritingService = siriWritingService;
        this.generatedExportRepository = generatedExportRepository;
        this.publishedJourneyRepository = publishedJourneyRepository;
    }

    @Transactional
    public void generate() {
        final StopWatch stopWatch = StopWatch.createStarted();
        SiriVmStats stats = SiriVmStats.empty();
        int outputSize = 0;
        Stage stage = Stage.PREPARE;
        Long journeySourceVersion = null;
        ZonedDateTime journeySourceGeneratedAt = null;
        String unavailableReason = "NULL";
        try {
            final VmGenerationContext context = prepareContext();
            journeySourceVersion = context.journeySourceVersion();
            journeySourceGeneratedAt = context.journeySourceGeneratedAt();
            final long locationsReceived = context.locations().size();
            // Seeded now (not just derived from result.stats() below) so a failure in BUILD/VALIDATE/PERSIST
            // still reports the true received count on its error event, instead of falling back to
            // SiriVmStats.empty()'s locationsReceived=0 and hiding the affected batch size.
            stats = new SiriVmStats(locationsReceived, 0);

            stage = Stage.BUILD;
            final SiriVmResult result = context.vmService().buildVmDocumentWithStats(context.locations(), context.now());
            stats = result.stats();
            outputSize = result.bytes().length;

            stage = Stage.VALIDATE;
            if (!siriWritingService.isSchemaValid(result.bytes())) {
                // Never publish structurally invalid SIRI: skip the persist so the last good feed keeps serving.
                throw new InvalidSiriOutputException("SIRI-VM output failed structural (JAXB) validation");
            }

            stage = Stage.PERSIST;
            final GeneratedExport export = new GeneratedExport();
            export.data = result.bytes();
            export.created = DateProvider.nowInHelsinki();
            export.fileName = "siri-vm.xml";
            generatedExportRepository.persist(List.of(export));

            stage = Stage.COMPLETE;
            logGenerationEvent(resolveOutcome(locationsReceived, stats), "NULL", stage,
                    stopWatch.getDuration().toMillis(), stats, outputSize,
                    journeySourceVersion, journeySourceGeneratedAt, unavailableReason, null);
        } catch (final Exception e) {
            if (e instanceof final PublishedJourneysUnavailableException pjue) {
                unavailableReason = pjue.reason().name();
            } else if (e instanceof PetiUnavailableException) {
                unavailableReason = PetiUnavailableException.REASON;
            }
            logGenerationEvent("error", e.getClass().getSimpleName(), stage, stopWatch.getDuration().toMillis(),
                    stats, outputSize, journeySourceVersion, journeySourceGeneratedAt, unavailableReason, e);
        }
    }

    /** Where a generation cycle got to — emitted as {@code stage=…} so an error line says where it failed. */
    private enum Stage { PREPARE, BUILD, VALIDATE, PERSIST, COMPLETE }

    /** Loads the live train locations + wires the VM collaborators against the newest NeTEx-published dataset. */
    private VmGenerationContext prepareContext() {
        final LocalDate today = DateProvider.dateInHelsinki();
        // Shared with SIRI-ET / the NeTEx persist window so the published journey refs SIRI-VM reads never
        // drift from the ones SIRI-ET and NeTEx itself use.
        final List<LocalDate> operatingDates = OperatingDayWindow.dates(today);

        final List<Station> stations = stationRepository.findAll();

        final DbSources dbSources = buildDbSources(operatingDates);
        final JourneyRefResolver journeyRefResolver = dbSources.journeyRefResolver();
        log.info("event=rail.siri.vm.prepare rail.siri.vm.journey_source=db");

        final InMemoryStationUicLookup stationUicLookup = new InMemoryStationUicLookup(stations);
        final InMemoryStationNameLookup stationNameLookup = new InMemoryStationNameLookup(stations);

        final PetiUicMatcher matcher = petiStopSource.getMatcher();
        if (matcher.matchedCount() == 0) {
            throw new PetiUnavailableException("PETI stop snapshot is empty after warm-up");
        }
        final SiriStopResolver siriStopResolver = new SiriStopResolver(matcher);

        final List<Long> locationIds = trainLocationRepository.findLatestForPassengerTrains(
                DateProvider.nowInHelsinki().minusMinutes(LOCATION_MAX_AGE_MINUTES));
        final List<GTFSTrainLocation> rawLocations = gtfsTrainRepository.getTrainLocations(locationIds);
        final ZonedDateTime now = DateProvider.nowInHelsinki();

        // Trains whose upcoming-stop query (GTFSTrainRepository.getTrainLocations) found nothing to report - a
        // train that has already arrived at its terminus has none left, so its stationShortCode comes back
        // null (see that query's own javadoc). Batched up-front for every such train in this cycle (one
        // collection query), same rationale as plannedTrackFallbackTrainIds below.
        final Set<TrainId> terminusFallbackTrainIds = rawLocations.stream()
                .filter(location -> location.getStationShortCode() == null)
                .map(location -> new TrainId(location.getTrainNumber(), location.getDepartureDate()))
                .collect(Collectors.toSet());
        final Map<TrainId, List<GTFSTimeTableRow>> terminusFallbackRowsByTrainId = terminusFallbackTrainIds.isEmpty()
                ? Map.of()
                : gtfsTrainRepository
                        .findBySourceVersionAndIdIn(ANY_SOURCE_VERSION, terminusFallbackTrainIds)
                        .stream()
                        .collect(Collectors.toMap(train -> train.id, train -> train.timeTableRows));
        final List<GTFSTrainLocation> locations = rawLocations.stream()
                .map(location -> resolveTerminusFallback(location, terminusFallbackRowsByTrainId))
                .toList();

        // Only consulted when a location's live track can't be used directly (see
        // VmJourneyConverter.needsPlannedTrackFallback / resolveMonitoredCallStopRef): batched up-front for every
        // such train in this cycle (one collection query), rather than one findBySourceVersionAndIdIn call per
        // fallback train - GTFSTrain.timeTableRows is EAGER, so a per-train call here would otherwise fetch that
        // train's full row set on every single generation cycle.
        final Set<TrainId> plannedTrackFallbackTrainIds = locations.stream()
                .filter(VmJourneyConverter::needsPlannedTrackFallback)
                .map(location -> new TrainId(location.getTrainNumber(), location.getDepartureDate()))
                .collect(Collectors.toSet());
        // Trains already fetched above (terminusFallbackRowsByTrainId) are skipped here - a train needing both
        // fallbacks (e.g. arrived at a terminus whose track also turns out to be unknown) must not have its row
        // set fetched twice.
        final Set<TrainId> missingTrainIdsToFetch = plannedTrackFallbackTrainIds.stream()
                .filter(trainId -> !terminusFallbackRowsByTrainId.containsKey(trainId))
                .collect(Collectors.toSet());
        final Map<TrainId, List<GTFSTimeTableRow>> missingRowsByTrainId = missingTrainIdsToFetch.isEmpty()
                ? Map.of()
                : gtfsTrainRepository
                        .findBySourceVersionAndIdIn(ANY_SOURCE_VERSION, missingTrainIdsToFetch)
                        .stream()
                        .collect(Collectors.toMap(train -> train.id, train -> train.timeTableRows));
        final Map<TrainId, List<GTFSTimeTableRow>> timeTableRowsByTrainId = new HashMap<>(terminusFallbackRowsByTrainId);
        timeTableRowsByTrainId.putAll(missingRowsByTrainId);
        final TimeTableRowsLookup timeTableRowsLookup = (trainNumber, departureDate) ->
                timeTableRowsByTrainId.getOrDefault(new TrainId(trainNumber, departureDate), List.of());

        final VmJourneyConverter converter = new VmJourneyConverter(journeyRefResolver, stationUicLookup,
                siriStopResolver, stationNameLookup, dbSources.plannedTrackLookup(), timeTableRowsLookup);
        final VmJourneyMarshaller marshaller =
                new VmJourneyMarshaller(siriWritingService, NeTExIdGenerator.CODESPACE, NeTExIdGenerator.CODESPACE);
        final SiriVmService vmService = new SiriVmService(converter, marshaller);

        return new VmGenerationContext(vmService, locations, now,
                dbSources.datasetVersion(), dbSources.newestGeneratedAt());
    }

    /// Fills in `location`'s stop fields from its train's arrived terminus (see
    /// {@code CommercialStopVisits#resolveTerminusFallback}) when {@code GTFSTrainRepository.getTrainLocations}'
    /// own "next stop" query resolved nothing for it (a {@code null} {@code stationShortCode}) - returns
    /// `location` unchanged otherwise, or when no terminus row is found (e.g. every commercial stop happens to
    /// be cancelled).
    private static GTFSTrainLocation resolveTerminusFallback(final GTFSTrainLocation location,
            final Map<TrainId, List<GTFSTimeTableRow>> rowsByTrainId) {
        if (location.getStationShortCode() != null) {
            return location;
        }
        final TrainId trainId = new TrainId(location.getTrainNumber(), location.getDepartureDate());
        final List<GTFSTimeTableRow> rows = rowsByTrainId.getOrDefault(trainId, List.of());
        final List<CommercialStopVisits.Stop> stops = CommercialStopVisits.of(rows);
        return CommercialStopVisits.resolveTerminusFallback(stops)
                .<GTFSTrainLocation>map(terminusRow -> new TerminusFallbackTrainLocation(location, terminusRow))
                .orElse(location);
    }

    /**
     * The DB read path: builds the journey ref lookup from the newest NeTEx-published dataset version. Throws
     * {@link PublishedJourneysUnavailableException} when the published NeTEx is missing, has no rows for the
     * operating days, or is stale — SIRI-VM generation then fails for the cycle rather than resolving live and
     * risking drift from the published plan.
     */
    private DbSources buildDbSources(final List<LocalDate> operatingDates) {
        final Long version = publishedJourneyRepository.getMaxDatasetVersion();
        if (version == null) {
            throw new PublishedJourneysUnavailableException(
                    PublishedJourneysUnavailableException.Reason.MISSING,
                    "no NeTEx-published journeys in the database");
        }

        final List<NeTExPublishedJourney> journeys =
                publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(version, operatingDates);
        if (journeys.isEmpty()) {
            throw new PublishedJourneysUnavailableException(
                    PublishedJourneysUnavailableException.Reason.EMPTY,
                    "no NeTEx-published journeys for operating days " + operatingDates);
        }

        final ZonedDateTime newestGeneratedAt = journeys.stream()
                .map(j -> j.generatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        final ZonedDateTime staleThreshold = DateProvider.nowInHelsinki().minusHours(JOURNEY_SOURCE_MAX_AGE_HOURS);
        if (newestGeneratedAt == null || newestGeneratedAt.isBefore(staleThreshold)) {
            throw new PublishedJourneysUnavailableException(
                    PublishedJourneysUnavailableException.Reason.STALE,
                    "stale NeTEx-published journeys, newestGeneratedAt=" + newestGeneratedAt);
        }

        final Map<TrainId, ResolvedJourney> resolvedByTrainId = new HashMap<>();
        // Same (trainNumber,departureDate) -> stationShortCode -> visitIndex -> plannedTrack shape as SIRI-ET's
        // MapPlannedTrackLookup, built from the same already-fetched j.tracks — VM's live track fallback needs
        // no extra query for this, only for the visitIndex (see TimeTableRowsLookup wiring in prepareContext()).
        final Map<TrainId, Map<String, Map<Integer, String>>> tracksByTrainId = new HashMap<>();
        for (final NeTExPublishedJourney j : journeys) {
            resolvedByTrainId.put(j.trainId, new ResolvedJourney(
                    new ServiceJourneyId(j.serviceJourneyId),
                    new DataFrameRef(j.trainId.departureDate.toString()),
                    new LineId(j.lineId),
                    j.operatorRef != null ? new OperatorRef(j.operatorRef) : null,
                    j.journeyPatternRef != null ? new JourneyPatternRef(j.journeyPatternRef) : null,
                    endpointOf(j.tracks, 0),
                    endpointOf(j.tracks, j.tracks.size() - 1)));
            for (final NeTExPublishedJourneyTrack t : j.tracks) {
                if (t.plannedTrack != null && t.stationShortCode != null) {
                    tracksByTrainId.computeIfAbsent(j.trainId, _ -> new HashMap<>())
                            .computeIfAbsent(t.stationShortCode, _ -> new HashMap<>())
                            .put(t.visitIndex, t.plannedTrack);
                }
            }
        }

        return new DbSources(new PublishedJourneyRefResolver(resolvedByTrainId),
                new MapPlannedTrackLookup(tracksByTrainId), version, newestGeneratedAt);
    }

    /**
     * The journey's first ({@code index=0}) or last ({@code index=tracks.size()-1}) commercial stop, for
     * SIRI-VM's optional {@code OriginRef}/{@code DestinationRef}. {@code null} when the journey has no tracks
     * or the stop's station/track is itself unknown.
     */
    private static JourneyEndpoint endpointOf(final List<NeTExPublishedJourneyTrack> tracks, final int index) {
        if (tracks.isEmpty() || index < 0 || index >= tracks.size()) {
            return null;
        }
        final NeTExPublishedJourneyTrack t = tracks.get(index);
        return t.stationShortCode != null ? new JourneyEndpoint(t.stationShortCode, t.plannedTrack) : null;
    }

    /** The DB-read journey ref + planned-track lookups, and the published dataset version/age used for it. */
    private record DbSources(JourneyRefResolver journeyRefResolver, PlannedTrackLookup plannedTrackLookup,
            long datasetVersion, ZonedDateTime newestGeneratedAt) {}

    private record VmGenerationContext(SiriVmService vmService, List<GTFSTrainLocation> locations, ZonedDateTime now,
            Long journeySourceVersion, ZonedDateTime journeySourceGeneratedAt) {}

    /** An empty feed despite having candidate locations is a real coverage loss, not a routine skip. */
    private static String resolveOutcome(final long locationsReceived, final SiriVmStats stats) {
        final boolean emptyFeed = locationsReceived > 0 && stats.activitiesEmitted() == 0;
        return emptyFeed ? "partial" : "success";
    }

    /**
     * Emits the one-line {@code rail.siri.generation} wide event — same field set on every outcome (zeros /
     * NULL where unavailable); the level tracks the outcome (success=info, partial=warn, error=error). On
     * error the throwable rides along on this same line, so the event is never logged twice.
     */
    private void logGenerationEvent(final String outcome, final String errorType, final Stage stage,
            final long durationMs, final SiriVmStats stats, final int outputSize,
            final Long journeySourceVersion, final ZonedDateTime journeySourceGeneratedAt,
            final String unavailableReason, final Throwable thrown) {
        final String datasetVersion = journeySourceVersion != null ? journeySourceVersion.toString() : "NULL";
        final String journeySourceAge = journeySourceGeneratedAt != null
                ? Long.toString(java.time.Duration.between(journeySourceGeneratedAt, DateProvider.nowInHelsinki()).getSeconds())
                : "NULL";
        final String line = StringUtil.format(
                "event=rail.siri.generation operation=generateSiriVm outcome={} error.type={} stage={} duration={} "
                        + "rail.siri.service=vm "
                        + "rail.siri.locations.received={} rail.siri.activities.emitted={} "
                        + "rail.siri.journey_source.dataset_version={} rail.siri.journey_source.age_s={} "
                        + "rail.siri.journey_source.unavailable_reason={} "
                        + "rail.netex.peti.snapshot.age_s={} rail.siri.output.size_bytes={}",
                outcome, errorType, stage.name().toLowerCase(Locale.ROOT), LogFields.durationSeconds(durationMs),
                stats.locationsReceived(), stats.activitiesEmitted(),
                datasetVersion, journeySourceAge, unavailableReason,
                petiStopSource.getSnapshotAgeSeconds(), outputSize);
        switch (outcome) {
            case "success" -> log.info(line);
            case "partial" -> log.warn(line);
            default -> log.error(line, thrown);
        }
    }
}
