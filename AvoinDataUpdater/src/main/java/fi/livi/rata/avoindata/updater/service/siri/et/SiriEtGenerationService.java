package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.InvalidSiriOutputException;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

@Service
public class SiriEtGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SiriEtGenerationService.class);

    private final ScheduleProviderService scheduleProviderService;
    private final StationRepository stationRepository;
    private final GTFSTrainRepository gtfsTrainRepository;
    private final PetiStopSource petiStopSource;
    private final NeTExService neTExService;
    private final NeTExEntityService neTExEntityService;
    private final NeTExIdGenerator neTExIdGenerator;
    private final SiriWritingService siriWritingService;
    private final GeneratedExportRepository generatedExportRepository;

    public SiriEtGenerationService(
            final ScheduleProviderService scheduleProviderService,
            final StationRepository stationRepository,
            final GTFSTrainRepository gtfsTrainRepository,
            final PetiStopSource petiStopSource,
            final NeTExService neTExService,
            final NeTExEntityService neTExEntityService,
            final NeTExIdGenerator neTExIdGenerator,
            final SiriWritingService siriWritingService,
            final GeneratedExportRepository generatedExportRepository) {
        this.scheduleProviderService = scheduleProviderService;
        this.stationRepository = stationRepository;
        this.gtfsTrainRepository = gtfsTrainRepository;
        this.petiStopSource = petiStopSource;
        this.neTExService = neTExService;
        this.neTExEntityService = neTExEntityService;
        this.neTExIdGenerator = neTExIdGenerator;
        this.siriWritingService = siriWritingService;
        this.generatedExportRepository = generatedExportRepository;
    }

    @Transactional
    public void generate() {
        final long start = System.currentTimeMillis();
        long trainsReceived = 0;
        SiriEtStats stats = SiriEtStats.empty();
        int outputSize = 0;
        Stage stage = Stage.PREPARE;
        try {
            final EtGenerationContext context = prepareContext();
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
                    System.currentTimeMillis() - start, trainsReceived, stats, outputSize);
        } catch (final Exception e) {
            logGenerationEvent("error", e.getClass().getSimpleName(), stage, System.currentTimeMillis() - start,
                    trainsReceived, stats, outputSize);
            // Companion line carries the message + stack trace; the wide line above stays scalar-only.
            log.error("event=rail.siri.generation operation=generateSiriEt outcome=error", e);
        }
    }

    /** Where a generation cycle got to — emitted as {@code stage=…} so an error line says where it failed. */
    private enum Stage { PREPARE, BUILD, VALIDATE, PERSIST, COMPLETE }

    /** Loads the operating-day schedules/stations, wires the ET collaborators and fetches the live trains. */
    private EtGenerationContext prepareContext() throws ExecutionException, InterruptedException {
        final LocalDate operatingDate = DateProvider.dateInHelsinki();

        final List<Schedule> adhocSchedules = scheduleProviderService.getAdhocSchedules(operatingDate);
        final List<Schedule> regularSchedules = scheduleProviderService.getRegularSchedules(operatingDate);

        final var stations = stationRepository.findAll();
        final Set<String> publishableStations = NeTExService.publishableStations(stations);

        // Reuse the exact passenger + winning-schedule selection (incl. dropping unpublishable-stop journeys)
        // that produced the published timetable, so every SIRI FramedVehicleJourneyRef resolves against the
        // static package.
        final Map<TrainId, Schedule> winningSchedules = neTExService.resolveWinningSchedules(
                adhocSchedules, regularSchedules, publishableStations, operatingDate, operatingDate);
        final Map<Long, Schedule> scheduleMap = new HashMap<>();
        winningSchedules.forEach((trainId, schedule) -> scheduleMap.put(trainId.trainNumber, schedule));

        final InMemoryStationUicLookup stationUicLookup = new InMemoryStationUicLookup(stations);
        final InMemoryStationNameLookup stationNameLookup = new InMemoryStationNameLookup(stations);
        final ScheduleMapPlannedTrackLookup plannedTrackLookup = new ScheduleMapPlannedTrackLookup(scheduleMap);

        final PetiUicMatcher matcher = petiStopSource.getMatcher();
        final SiriStopResolver siriStopResolver = new SiriStopResolver(matcher);

        final ScheduleMapJourneyRefResolver journeyRefResolver =
                new ScheduleMapJourneyRefResolver(scheduleMap, neTExEntityService, neTExIdGenerator);

        final List<GTFSTrain> trains = gtfsTrainRepository.findBySourceVersionGreaterThan(0L);
        final ZonedDateTime now = DateProvider.nowInHelsinki();

        final SiriEtService etService = new SiriEtService(
                journeyRefResolver, stationUicLookup, siriStopResolver,
                stationNameLookup, plannedTrackLookup,
                siriWritingService, NeTExIdGenerator.CODESPACE, NeTExIdGenerator.CODESPACE);

        return new EtGenerationContext(etService, trains, now);
    }

    private record EtGenerationContext(SiriEtService etService, List<GTFSTrain> trains, ZonedDateTime now) {}

    /**
     * Degraded-but-not-failed cycles. Two triggers: an empty feed despite having candidate trains, and any
     * commercial stop that couldn't be linked to a PETI quay ({@code unresolved_stop}) — a real coverage loss,
     * unlike the routinely-skipped non-passenger journeys ({@code unresolved_journey}).
     */
    private static String resolveOutcome(final long trainsReceived, final SiriEtStats stats) {
        final boolean emptyFeed = trainsReceived > 0 && stats.journeysEmitted() == 0;
        final boolean petiLinkageLoss = stats.skippedUnresolvedStop() > 0;
        return emptyFeed || petiLinkageLoss ? "partial" : "success";
    }

    /**
     * Emits the one-line {@code rail.siri.generation} wide event — same field set on every outcome (zeros /
     * NULL where unavailable); the level tracks the outcome (success=info, partial=warn, error=error).
     */
    private void logGenerationEvent(final String outcome, final String errorType, final Stage stage,
            final long durationMs, final long trainsReceived, final SiriEtStats stats, final int outputSize) {
        final String matchRate = stats.matchRate().isPresent()
                ? String.format(Locale.ROOT, "%.4f", stats.matchRate().getAsDouble())
                : "NULL";
        final String line = StringUtil.format(
                "event=rail.siri.generation operation=generateSiriEt outcome={} error.type={} stage={} duration_ms={} "
                        + "rail.siri.service=et "
                        + "rail.siri.trains.received={} rail.siri.journeys.emitted={} "
                        + "rail.siri.journeys.cancelled={} rail.siri.journeys.skipped.unresolved_journey={} "
                        + "rail.siri.journeys.skipped.unresolved_stop={} rail.siri.calls.total={} "
                        + "rail.siri.calls.recorded={} rail.siri.calls.estimated={} "
                        + "rail.siri.stop_refs.resolved.quay={} rail.siri.stop_refs.resolved.stop_place={} "
                        + "rail.siri.stop_refs.unresolved={} rail.siri.peti.match_rate={} "
                        + "rail.netex.peti.snapshot.age_s={} rail.siri.output.size_bytes={}",
                outcome, errorType, stage.name().toLowerCase(Locale.ROOT), durationMs, trainsReceived,
                stats.journeysEmitted(), stats.journeysCancelled(), stats.skippedUnresolvedJourney(),
                stats.skippedUnresolvedStop(), stats.callsTotal(), stats.callsRecorded(), stats.callsEstimated(),
                stats.stopRefsQuay(), stats.stopRefsStopPlace(), stats.stopRefsUnresolved(), matchRate,
                petiStopSource.getSnapshotAgeSeconds(), outputSize);
        switch (outcome) {
            case "success" -> log.info(line);
            case "partial" -> log.warn(line);
            default -> log.error(line);
        }
    }
}
