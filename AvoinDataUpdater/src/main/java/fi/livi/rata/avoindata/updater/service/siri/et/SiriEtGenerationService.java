package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        try {
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
            trainsReceived = trains.size();
            final ZonedDateTime now = DateProvider.nowInHelsinki();

            final SiriEtService etService = new SiriEtService(
                    journeyRefResolver, stationUicLookup, siriStopResolver,
                    stationNameLookup, plannedTrackLookup,
                    siriWritingService, NeTExIdGenerator.CODESPACE, NeTExIdGenerator.CODESPACE);
            final SiriEtResult result = etService.buildEtDocumentWithStats(trains, now);
            stats = result.stats();
            final byte[] bytes = siriWritingService.marshalToBytes(result.document());
            outputSize = bytes.length;

            final GeneratedExport export = new GeneratedExport();
            export.data = bytes;
            export.created = DateProvider.nowInHelsinki();
            export.fileName = "siri-et.xml";
            generatedExportRepository.persist(List.of(export));

            logGenerationEvent("success", "NULL", System.currentTimeMillis() - start,
                    trainsReceived, stats, outputSize);
        } catch (final Exception e) {
            logGenerationEvent("error", e.getClass().getSimpleName(), System.currentTimeMillis() - start,
                    trainsReceived, stats, outputSize);
            // Companion line carries the message + stack trace; the wide line above stays scalar-only.
            log.error("event=rail.siri.et.generation operation=generateSiriEt outcome=error", e);
        }
    }

    /**
     * Emits the one-line {@code rail.siri.et.generation} wide event — same field set on success and error
     * (zeros / NULL where unavailable), only the level differs (info vs error).
     */
    private void logGenerationEvent(final String outcome, final String errorType, final long durationMs,
            final long trainsReceived, final SiriEtStats stats, final int outputSize) {
        final String matchRate = stats.matchRate().isPresent()
                ? String.format(Locale.ROOT, "%.4f", stats.matchRate().getAsDouble())
                : "NULL";
        final String line = String.format(Locale.ROOT,
                "event=rail.siri.et.generation operation=generateSiriEt outcome=%s error.type=%s duration_ms=%d "
                        + "rail.siri.service=et "
                        + "rail.siri.et.trains.received=%d rail.siri.et.journeys.emitted=%d "
                        + "rail.siri.et.journeys.cancelled=%d rail.siri.et.journeys.skipped.unresolved_journey=%d "
                        + "rail.siri.et.journeys.skipped.unresolved_stop=%d rail.siri.et.calls.total=%d "
                        + "rail.siri.et.calls.recorded=%d rail.siri.et.calls.estimated=%d "
                        + "rail.siri.et.stop_refs.resolved.quay=%d rail.siri.et.stop_refs.resolved.stop_place=%d "
                        + "rail.siri.et.stop_refs.unresolved=%d rail.siri.et.peti.match_rate=%s "
                        + "rail.netex.peti.snapshot.age_s=%d rail.siri.et.output.size_bytes=%d",
                outcome, errorType, durationMs, trainsReceived,
                stats.journeysEmitted(), stats.journeysCancelled(), stats.skippedUnresolvedJourney(),
                stats.skippedUnresolvedStop(), stats.callsTotal(), stats.callsRecorded(), stats.callsEstimated(),
                stats.stopRefsQuay(), stats.stopRefsStopPlace(), stats.stopRefsUnresolved(), matchRate,
                petiStopSource.getSnapshotAgeSeconds(), outputSize);
        if ("success".equals(outcome)) {
            log.info(line);
        } else {
            log.error(line);
        }
    }
}
