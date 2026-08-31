package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import uk.org.siri.siri21.Siri;

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
        try {
            final long start = System.currentTimeMillis();
            final LocalDate operatingDate = DateProvider.dateInHelsinki();

            final List<Schedule> adhocSchedules = scheduleProviderService.getAdhocSchedules(operatingDate);
            final List<Schedule> regularSchedules = scheduleProviderService.getRegularSchedules(operatingDate);

            // Reuse the exact passenger + winning-schedule selection that produced the published timetable,
            // so every SIRI FramedVehicleJourneyRef resolves against the static package.
            final Map<TrainId, Schedule> winningSchedules = neTExService.resolveWinningSchedules(
                    adhocSchedules, regularSchedules, operatingDate, operatingDate);
            final Map<Long, Schedule> scheduleMap = new HashMap<>();
            winningSchedules.forEach((trainId, schedule) -> scheduleMap.put(trainId.trainNumber, schedule));

            final var stations = stationRepository.findAll();
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
            final Siri siri = etService.buildEtDocument(trains, now);
            final byte[] bytes = siriWritingService.marshalToBytes(siri);

            final GeneratedExport export = new GeneratedExport();
            export.data = bytes;
            export.created = DateProvider.nowInHelsinki();
            export.fileName = "siri-et.xml";
            generatedExportRepository.persist(List.of(export));

            final long duration = System.currentTimeMillis() - start;
            log.info("event=rail.siri.et.generation outcome=success duration_ms={} journeys={}", duration, trains.size());
        } catch (final Exception e) {
            log.error("event=rail.siri.et.generation outcome=failed", e);
        }
    }
}
