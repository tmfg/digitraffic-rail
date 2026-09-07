package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourneyTrack;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.netex.OperatingDayWindow;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.InvalidSiriOutputException;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyPatternRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;

@Service
public class SiriEtGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SiriEtGenerationService.class);

    // Published journeys older than this (vs. their newest generatedAt) are treated as stale → the cycle fails.
    private static final long JOURNEY_SOURCE_MAX_AGE_HOURS = 26;

    private final StationRepository stationRepository;
    private final GTFSTrainRepository gtfsTrainRepository;
    private final PetiStopSource petiStopSource;
    private final SiriWritingService siriWritingService;
    private final GeneratedExportRepository generatedExportRepository;
    private final NeTExPublishedJourneyRepository publishedJourneyRepository;

    public SiriEtGenerationService(
            final StationRepository stationRepository,
            final GTFSTrainRepository gtfsTrainRepository,
            final PetiStopSource petiStopSource,
            final SiriWritingService siriWritingService,
            final GeneratedExportRepository generatedExportRepository,
            final NeTExPublishedJourneyRepository publishedJourneyRepository) {
        this.stationRepository = stationRepository;
        this.gtfsTrainRepository = gtfsTrainRepository;
        this.petiStopSource = petiStopSource;
        this.siriWritingService = siriWritingService;
        this.generatedExportRepository = generatedExportRepository;
        this.publishedJourneyRepository = publishedJourneyRepository;
    }

    @Transactional
    public void generate() {
        final long start = System.currentTimeMillis();
        long trainsReceived = 0;
        SiriEtStats stats = SiriEtStats.empty();
        int outputSize = 0;
        Stage stage = Stage.PREPARE;
        // Journey-source correlation for the wide event: which NeTEx dataset this cycle read and how old it is,
        // or (on failure) why it was unusable. Populated once the source is resolved; stay NULL if we fail first.
        Long journeySourceVersion = null;
        ZonedDateTime journeySourceGeneratedAt = null;
        String unavailableReason = "NULL";
        try {
            final EtGenerationContext context = prepareContext();
            journeySourceVersion = context.journeySourceVersion();
            journeySourceGeneratedAt = context.journeySourceGeneratedAt();
            trainsReceived = context.trains().size();

            stage = Stage.BUILD;
            final SiriEtResult result = context.etService().buildEtDocumentWithStats(context.trains(), context.now());
            stats = result.stats();
            outputSize = result.bytes().length;

            stage = Stage.VALIDATE;
            if (!siriWritingService.isSchemaValid(result.bytes())) {
                // Never publish structurally invalid SIRI: skip the persist so the last good feed keeps serving.
                throw new InvalidSiriOutputException("SIRI-ET output failed structural (JAXB) validation");
            }

            stage = Stage.PERSIST;
            final GeneratedExport export = new GeneratedExport();
            export.data = result.bytes();
            export.created = DateProvider.nowInHelsinki();
            export.fileName = "siri-et.xml";
            generatedExportRepository.persist(List.of(export));

            stage = Stage.COMPLETE;
            logGenerationEvent(resolveOutcome(trainsReceived, stats), "NULL", stage,
                    System.currentTimeMillis() - start, trainsReceived, stats, outputSize,
                    journeySourceVersion, journeySourceGeneratedAt, unavailableReason);
        } catch (final Exception e) {
            if (e instanceof PublishedJourneysUnavailableException pjue) {
                unavailableReason = pjue.reason().name();
            }
            logGenerationEvent("error", e.getClass().getSimpleName(), stage, System.currentTimeMillis() - start,
                    trainsReceived, stats, outputSize, journeySourceVersion, journeySourceGeneratedAt,
                    unavailableReason);
            // Companion line carries the message + stack trace; the wide line above stays scalar-only.
            log.error("event=rail.siri.generation operation=generateSiriEt outcome=error", e);
        }
    }

    /** Where a generation cycle got to — emitted as {@code stage=…} so an error line says where it failed. */
    private enum Stage { PREPARE, BUILD, VALIDATE, PERSIST, COMPLETE }

    /** Loads the operating-day stations + published journey refs, wires the ET collaborators and fetches the live trains. */
    private EtGenerationContext prepareContext() {
        final LocalDate today = DateProvider.dateInHelsinki();
        // Shared with the NeTEx persist window (OperatingDayWindow) so the range SIRI reads and the range NeTEx
        // persisted can never drift apart; the train fetch uses the same Helsinki window so they roll over
        // together at Helsinki midnight.
        final List<LocalDate> operatingDates = OperatingDayWindow.dates(today);

        final List<Station> stations = stationRepository.findAll();

        // SIRI reads the journey/track refs from the NeTEx-published dataset — a hard dependency on the
        // published NeTEx. If it is missing or stale, buildDbSources throws and the cycle fails (no live
        // fallback), so the real-time feed can never drift from the published package.
        final DbSources dbSources = buildDbSources(operatingDates);
        final JourneyRefResolver journeyRefResolver = dbSources.journeyRefResolver();
        final PlannedTrackLookup plannedTrackLookup = dbSources.plannedTrackLookup();
        log.info("event=rail.siri.et.prepare rail.siri.et.journey_source=db");

        final InMemoryStationUicLookup stationUicLookup = new InMemoryStationUicLookup(stations);
        final InMemoryStationNameLookup stationNameLookup = new InMemoryStationNameLookup(stations);

        final PetiUicMatcher matcher = petiStopSource.getMatcher();
        final SiriStopResolver siriStopResolver = new SiriStopResolver(matcher);

        final List<GTFSTrain> trains = gtfsTrainRepository.findBySourceVersionAndIdIn(0L, dbSources.trainIds());
        final ZonedDateTime now = DateProvider.nowInHelsinki();

        final SiriEtService etService = new SiriEtService(
                journeyRefResolver, stationUicLookup, siriStopResolver,
                stationNameLookup, plannedTrackLookup,
                siriWritingService, NeTExIdGenerator.CODESPACE, NeTExIdGenerator.CODESPACE);

        return new EtGenerationContext(etService, trains, now,
                dbSources.datasetVersion(), dbSources.newestGeneratedAt());
    }

    /**
     * The DB read path: builds the journey/track lookups from the newest NeTEx-published dataset version.
     * Throws {@link PublishedJourneysUnavailableException} when the published NeTEx is missing, has no rows for
     * the operating days, or is stale (newest {@code generatedAt} older than {@link #JOURNEY_SOURCE_MAX_AGE_HOURS})
     * — SIRI-ET generation then fails for the cycle rather than resolving live and risking drift.
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
        final Map<TrainId, Map<String, String>> tracksByTrainId = new HashMap<>();
        for (final NeTExPublishedJourney j : journeys) {
            resolvedByTrainId.put(j.trainId, new ResolvedJourney(
                    new ServiceJourneyId(j.serviceJourneyId),
                    new DataFrameRef(j.trainId.departureDate.toString()),
                    new LineId(j.lineId),
                    j.operatorRef != null ? new OperatorRef(j.operatorRef) : null,
                    j.journeyPatternRef != null ? new JourneyPatternRef(j.journeyPatternRef) : null));
            for (final NeTExPublishedJourneyTrack t : j.tracks) {
                if (t.plannedTrack != null && t.stationShortCode != null) {
                    tracksByTrainId.computeIfAbsent(j.trainId, k -> new HashMap<>())
                            .put(t.stationShortCode, t.plannedTrack);
                }
            }
        }

        return new DbSources(
                new PublishedJourneyRefResolver(resolvedByTrainId),
                new MapPlannedTrackLookup(tracksByTrainId),
                Set.copyOf(resolvedByTrainId.keySet()),
                version, newestGeneratedAt);
    }

    /** The DB-read journey/track lookups + the published (trainNumber, date) set to fetch live trains for. */
    private record DbSources(JourneyRefResolver journeyRefResolver, PlannedTrackLookup plannedTrackLookup,
            Set<TrainId> trainIds, long datasetVersion, ZonedDateTime newestGeneratedAt) {}

    private record EtGenerationContext(SiriEtService etService, List<GTFSTrain> trains, ZonedDateTime now,
            Long journeySourceVersion, ZonedDateTime journeySourceGeneratedAt) {}

    /**
     * Degraded-but-not-failed cycles. Two triggers: an empty feed despite having candidate trains, and any
     * commercial stop that couldn't be linked to a PETI quay (no stop place, or no quay) — a real coverage
     * loss, unlike the routinely-skipped non-passenger journeys ({@code unresolved_journey}).
     */
    private static String resolveOutcome(final long trainsReceived, final SiriEtStats stats) {
        final boolean emptyFeed = trainsReceived > 0 && stats.journeysEmitted() == 0;
        final boolean petiLinkageLoss =
                stats.skippedUnresolvedStopNoStop() > 0 || stats.skippedUnresolvedStopNoQuay() > 0;
        return emptyFeed || petiLinkageLoss ? "partial" : "success";
    }

    /**
     * Emits the one-line {@code rail.siri.generation} wide event — same field set on every outcome (zeros /
     * NULL where unavailable); the level tracks the outcome (success=info, partial=warn, error=error).
     */
    private void logGenerationEvent(final String outcome, final String errorType, final Stage stage,
            final long durationMs, final long trainsReceived, final SiriEtStats stats, final int outputSize,
            final Long journeySourceVersion, final ZonedDateTime journeySourceGeneratedAt,
            final String unavailableReason) {
        final String matchRate = stats.matchRate().isPresent()
                ? String.format(Locale.ROOT, "%.4f", stats.matchRate().getAsDouble())
                : "NULL";
        final String datasetVersion = journeySourceVersion != null ? journeySourceVersion.toString() : "NULL";
        final String journeySourceAge = journeySourceGeneratedAt != null
                ? Long.toString(Duration.between(journeySourceGeneratedAt, DateProvider.nowInHelsinki()).getSeconds())
                : "NULL";
        final String line = StringUtil.format(
                "event=rail.siri.generation operation=generateSiriEt outcome={} error.type={} stage={} duration_ms={} "
                        + "rail.siri.service=et "
                        + "rail.siri.trains.received={} rail.siri.journeys.emitted={} "
                        + "rail.siri.journeys.cancelled={} rail.siri.journeys.skipped.unresolved_journey={} "
                        + "rail.siri.journeys.skipped.unresolved_stop.no_stop={} "
                        + "rail.siri.journeys.skipped.unresolved_stop.no_quay={} "
                        + "rail.siri.journeys.skipped.completed_carryover={} rail.siri.calls.total={} "
                        + "rail.siri.calls.recorded={} rail.siri.calls.estimated={} "
                        + "rail.siri.stop_refs.resolved.quay={} rail.siri.stop_refs.resolved.stop_place={} "
                        + "rail.siri.stop_refs.unresolved={} rail.siri.peti.match_rate={} "
                        + "rail.siri.journey_source.dataset_version={} rail.siri.journey_source.age_s={} "
                        + "rail.siri.journey_source.unavailable_reason={} "
                        + "rail.netex.peti.snapshot.age_s={} rail.siri.output.size_bytes={}",
                outcome, errorType, stage.name().toLowerCase(Locale.ROOT), durationMs, trainsReceived,
                stats.journeysEmitted(), stats.journeysCancelled(), stats.skippedUnresolvedJourney(),
                stats.skippedUnresolvedStopNoStop(), stats.skippedUnresolvedStopNoQuay(),
                stats.skippedCompletedCarryover(), stats.callsTotal(),
                stats.callsRecorded(), stats.callsEstimated(),
                stats.stopRefsQuay(), stats.stopRefsStopPlace(), stats.stopRefsUnresolved(), matchRate,
                datasetVersion, journeySourceAge, unavailableReason,
                petiStopSource.getSnapshotAgeSeconds(), outputSize);
        switch (outcome) {
            case "success" -> log.info(line);
            case "partial" -> log.warn(line);
            default -> log.error(line);
        }
    }
}
