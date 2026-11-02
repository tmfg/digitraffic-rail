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

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.gtfs.RealtimeFeedFileNames;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourneyTrack;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import static fi.livi.digitraffic.common.logging.LogFields.*;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.netex.OperatingDayWindow;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.InvalidSiriOutputException;
import fi.livi.rata.avoindata.updater.service.siri.common.JourneyPatternRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.PetiUnavailableException;
import fi.livi.rata.avoindata.updater.service.siri.common.PublishedJourneysUnavailableException;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriFeed;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;

import static fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository.*;

@Service
public class SiriEtGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SiriEtGenerationService.class);

    private final StationRepository stationRepository;
    private final GTFSTrainRepository gtfsTrainRepository;
    private final PetiStopSource petiStopSource;
    private final SiriWritingService siriWritingService;
    private final GeneratedExportRepository generatedExportRepository;
    private final NeTExPublishedJourneyRepository publishedJourneyRepository;
    private final LastUpdateService lastUpdateService;

    public SiriEtGenerationService(
            final StationRepository stationRepository,
            final GTFSTrainRepository gtfsTrainRepository,
            final PetiStopSource petiStopSource,
            final SiriWritingService siriWritingService,
            final GeneratedExportRepository generatedExportRepository,
            final NeTExPublishedJourneyRepository publishedJourneyRepository,
            final LastUpdateService lastUpdateService) {
        this.stationRepository = stationRepository;
        this.gtfsTrainRepository = gtfsTrainRepository;
        this.petiStopSource = petiStopSource;
        this.siriWritingService = siriWritingService;
        this.generatedExportRepository = generatedExportRepository;
        this.publishedJourneyRepository = publishedJourneyRepository;
        this.lastUpdateService = lastUpdateService;
    }

    @Transactional
    public void generate() {
        final StopWatch stopWatch = StopWatch.createStarted();
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
            if (!siriWritingService.isSchemaValid(result.bytes(), SiriFeed.ET)) {
                // Never publish structurally invalid SIRI: skip the persist so the last good feed keeps serving.
                throw new InvalidSiriOutputException("SIRI-ET output failed SIRI 2.0 XSD validation");
            }

            stage = Stage.PERSIST;
            final GeneratedExport export = new GeneratedExport();
            export.data = result.bytes();
            export.created = DateProvider.nowInHelsinki();
            export.fileName = RealtimeFeedFileNames.SIRI_ET;
            generatedExportRepository.persist(List.of(export));
            lastUpdateService.update(LastUpdateService.LastUpdatedType.SIRI_ET);

            stage = Stage.COMPLETE;
            logGenerationEvent(resolveOutcome(trainsReceived, stats), "NULL", stage,
                    stopWatch.getDuration().toMillis(), trainsReceived, stats, outputSize,
                    journeySourceVersion, journeySourceGeneratedAt, unavailableReason, null);
        } catch (final Exception e) {
            if (e instanceof final PublishedJourneysUnavailableException pjue) {
                unavailableReason = pjue.reason().name();
            } else if (e instanceof PetiUnavailableException) {
                unavailableReason = PetiUnavailableException.REASON;
            }
            logGenerationEvent("error", e.getClass().getSimpleName(), stage, stopWatch.getDuration().toMillis(),
                    trainsReceived, stats, outputSize, journeySourceVersion, journeySourceGeneratedAt,
                    unavailableReason, e);
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
        log.atInfo()
                .addKeyValue("event", "rail.siri.et.prepare")
                .addKeyValue("rail.siri.et.journey_source", "db")
                .log("SIRI-ET prepare");

        final InMemoryStationUicLookup stationUicLookup = new InMemoryStationUicLookup(stations);
        final InMemoryStationNameLookup stationNameLookup = new InMemoryStationNameLookup(stations);

        final PetiUicMatcher matcher = petiStopSource.getMatcher();
        if (matcher.matchedCount() == 0) {
            throw new PetiUnavailableException("PETI stop snapshot is empty after warm-up");
        }
        final SiriStopResolver siriStopResolver = new SiriStopResolver(matcher);

        final List<GTFSTrain> trains = gtfsTrainRepository
                .findBySourceVersionAndIdIn(ANY_SOURCE_VERSION, dbSources.trainIds());
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
     * the operating days, or is stale (newest {@code generatedAt} older than {@link PublishedJourneysUnavailableException#MAX_AGE_HOURS})
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
        final ZonedDateTime staleThreshold = DateProvider.nowInHelsinki().minusHours(PublishedJourneysUnavailableException.MAX_AGE_HOURS);
        if (newestGeneratedAt == null || newestGeneratedAt.isBefore(staleThreshold)) {
            throw new PublishedJourneysUnavailableException(
                    PublishedJourneysUnavailableException.Reason.STALE,
                    "stale NeTEx-published journeys, newestGeneratedAt=" + newestGeneratedAt);
        }

        final Map<TrainId, ResolvedJourney> resolvedByTrainId = new HashMap<>();
        final Map<TrainId, Map<String, Map<Integer, String>>> tracksByTrainId = new HashMap<>();
        for (final NeTExPublishedJourney j : journeys) {
            resolvedByTrainId.put(j.trainId, new ResolvedJourney(
                    new ServiceJourneyId(j.serviceJourneyId),
                    new DataFrameRef(j.trainId.departureDate.toString()),
                    new LineId(j.lineId),
                    j.operatorRef != null ? new OperatorRef(j.operatorRef) : null,
                    j.journeyPatternRef != null ? new JourneyPatternRef(j.journeyPatternRef) : null));
            for (final NeTExPublishedJourneyTrack t : j.tracks) {
                if (t.plannedTrack != null && t.stationShortCode != null) {
                    tracksByTrainId.computeIfAbsent(j.trainId, _ -> new HashMap<>())
                            .computeIfAbsent(t.stationShortCode, _ -> new HashMap<>())
                            .put(t.visitIndex, t.plannedTrack);
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
     * absent where unavailable); the level tracks the outcome (success=info, partial=warn, error=error). On
     * error the throwable rides along on this same line, so the event is never logged twice.
     */
    private void logGenerationEvent(final String outcome, final String errorType, final Stage stage,
            final long durationMs, final long trainsReceived, final SiriEtStats stats, final int outputSize,
            final Long journeySourceVersion, final ZonedDateTime journeySourceGeneratedAt,
            final String unavailableReason, final Throwable thrown) {
        final Double matchRate = stats.matchRate().isPresent() ? stats.matchRate().getAsDouble() : null;
        final Double journeySourceAge = journeySourceGeneratedAt != null
                ? durationSeconds(
                        Duration.between(journeySourceGeneratedAt, DateProvider.nowInHelsinki()).toMillis())
                : null;
        final LoggingEventBuilder event = switch (outcome) {
            case "success" -> log.atInfo();
            case "partial" -> log.atWarn();
            default -> log.atError();
        };
        event.addKeyValue("event", "rail.siri.generation")
                .addKeyValue("operation", "generateSiriEt")
                .addKeyValue("outcome", outcome)
                .addKeyValue("error.type", errorType)
                .addKeyValue("stage", stage.name().toLowerCase(Locale.ROOT))
                .addKeyValue("duration", durationSeconds(durationMs))
                .addKeyValue("rail.siri.service", SiriFeed.ET.logName())
                .addKeyValue("rail.siri.trains.received", trainsReceived)
                .addKeyValue("rail.siri.journeys.emitted", stats.journeysEmitted())
                .addKeyValue("rail.siri.journeys.cancelled", stats.journeysCancelled())
                .addKeyValue("rail.siri.journeys.skipped.unresolved_journey", stats.skippedUnresolvedJourney())
                .addKeyValue("rail.siri.journeys.skipped.unresolved_stop.no_stop", stats.skippedUnresolvedStopNoStop())
                .addKeyValue("rail.siri.journeys.skipped.unresolved_stop.no_quay", stats.skippedUnresolvedStopNoQuay())
                .addKeyValue("rail.siri.journeys.skipped.completed_carryover", stats.skippedCompletedCarryover())
                .addKeyValue("rail.siri.calls.total", stats.callsTotal())
                .addKeyValue("rail.siri.calls.recorded", stats.callsRecorded())
                .addKeyValue("rail.siri.calls.estimated", stats.callsEstimated())
                .addKeyValue("rail.siri.stop_refs.resolved.quay", stats.stopRefsQuay())
                .addKeyValue("rail.siri.stop_refs.resolved.stop_place", stats.stopRefsStopPlace())
                .addKeyValue("rail.siri.stop_refs.unresolved", stats.stopRefsUnresolved())
                .addKeyValue("rail.siri.peti.match_rate", matchRate)
                .addKeyValue("rail.siri.journey_source.dataset_version", journeySourceVersion)
                .addKeyValue("rail.siri.journey_source.age", journeySourceAge)
                .addKeyValue("rail.siri.journey_source.unavailable_reason", unavailableReason)
                .addKeyValue("rail.netex.peti.snapshot.age", petiStopSource.getSnapshotAgeSeconds())
                .addKeyValue("rail.siri.output.size", outputSize)
                .setCause(thrown)
                .log("SIRI-ET generation");
    }
}
