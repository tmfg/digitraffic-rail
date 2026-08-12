package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriJourneyResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.TodaysScheduleService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import uk.org.siri.siri21.Siri;

@Service
public class SiriEtGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SiriEtGenerationService.class);

    private final ScheduleProviderService scheduleProviderService;
    private final TodaysScheduleService todaysScheduleService;
    private final StationRepository stationRepository;
    private final GTFSTrainRepository gtfsTrainRepository;
    private final PetiStopSource petiStopSource;
    private final SiriJourneyResolver siriJourneyResolver;
    private final SiriWritingService siriWritingService;
    private final GeneratedExportRepository generatedExportRepository;
    private final String codespace;

    public SiriEtGenerationService(
            final ScheduleProviderService scheduleProviderService,
            final TodaysScheduleService todaysScheduleService,
            final StationRepository stationRepository,
            final GTFSTrainRepository gtfsTrainRepository,
            final PetiStopSource petiStopSource,
            final SiriJourneyResolver siriJourneyResolver,
            final SiriWritingService siriWritingService,
            final GeneratedExportRepository generatedExportRepository,
            @Value("${updater.siri.codespace:TEST}") final String codespace) {
        this.scheduleProviderService = scheduleProviderService;
        this.todaysScheduleService = todaysScheduleService;
        this.stationRepository = stationRepository;
        this.gtfsTrainRepository = gtfsTrainRepository;
        this.petiStopSource = petiStopSource;
        this.siriJourneyResolver = siriJourneyResolver;
        this.siriWritingService = siriWritingService;
        this.generatedExportRepository = generatedExportRepository;
        this.codespace = codespace;
    }

    @Transactional
    public void generate() {
        try {
            final long start = System.currentTimeMillis();
            final LocalDate operatingDate = DateProvider.dateInHelsinki();

            final List<Schedule> adhocSchedules = scheduleProviderService.getAdhocSchedules(operatingDate);
            final List<Schedule> regularSchedules = scheduleProviderService.getRegularSchedules(operatingDate);

            final List<Schedule> winningSchedules = todaysScheduleService.getDaysSchedules(
                    operatingDate, adhocSchedules, regularSchedules);
            final Map<Long, Schedule> scheduleMap = winningSchedules.stream()
                    .collect(Collectors.toMap(s -> s.trainNumber, Function.identity(), (a, b) -> a));

            final DbStationUicLookup stationUicLookup = new DbStationUicLookup(stationRepository.findAll());

            final PetiUicMatcher matcher = petiStopSource.getMatcher();
            final SiriStopResolver siriStopResolver = new SiriStopResolver(matcher);

            final ScheduleMapJourneyRefResolver journeyRefResolver =
                    new ScheduleMapJourneyRefResolver(scheduleMap, siriJourneyResolver);

            final List<GTFSTrain> trains = gtfsTrainRepository.findBySourceVersionGreaterThan(0L);
            final ZonedDateTime now = DateProvider.nowInHelsinki();

            final SiriEtService etService = new SiriEtService(
                    journeyRefResolver, stationUicLookup, siriStopResolver,
                    siriWritingService, codespace, codespace);
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
