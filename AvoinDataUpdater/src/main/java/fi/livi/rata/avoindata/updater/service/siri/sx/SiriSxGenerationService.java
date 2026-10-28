package fi.livi.rata.avoindata.updater.service.siri.sx;

import java.nio.charset.StandardCharsets;
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
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.dao.rami.RamiFirstCreated;
import fi.livi.rata.avoindata.common.dao.rami.RamiMessageRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessage;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.InvalidSiriOutputException;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.PetiUnavailableException;
import fi.livi.rata.avoindata.updater.service.siri.common.PublishedJourneysUnavailableException;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.et.InMemoryStationUicLookup;
import fi.livi.rata.avoindata.updater.service.siri.et.PublishedJourneyRefResolver;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;

import static fi.livi.digitraffic.common.logging.LogFields.durationSeconds;

/**
 * Builds the SIRI-SX file from RAMI messages and stores it. A new file is stored only when the situations have
 * changed; otherwise the time of the last stored file is updated.
 */
@Service
public class SiriSxGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SiriSxGenerationService.class);

    static final String FILE_NAME = "siri-sx.xml";


    private final RamiMessageRepository ramiMessageRepository;
    private final StationRepository stationRepository;
    private final NeTExPublishedJourneyRepository publishedJourneyRepository;
    private final PetiStopSource petiStopSource;
    private final SiriWritingService siriWritingService;
    private final GeneratedExportRepository generatedExportRepository;
    private final LastUpdateService lastUpdateService;

    public SiriSxGenerationService(
            final RamiMessageRepository ramiMessageRepository,
            final StationRepository stationRepository,
            final NeTExPublishedJourneyRepository publishedJourneyRepository,
            final PetiStopSource petiStopSource,
            final SiriWritingService siriWritingService,
            final GeneratedExportRepository generatedExportRepository,
            final LastUpdateService lastUpdateService) {
        this.ramiMessageRepository = ramiMessageRepository;
        this.stationRepository = stationRepository;
        this.publishedJourneyRepository = publishedJourneyRepository;
        this.petiStopSource = petiStopSource;
        this.siriWritingService = siriWritingService;
        this.generatedExportRepository = generatedExportRepository;
        this.lastUpdateService = lastUpdateService;
    }

    @Transactional
    public void generate() {
        final StopWatch stopWatch = StopWatch.createStarted();
        Stage stage = Stage.PREPARE;
        int messages = 0;
        SxResult result = null;
        int outputSize = 0;
        String stored = "NULL";
        String journeyProblem = null;
        try {
            final ZonedDateTime now = DateProvider.nowInHelsinki();
            final List<RamiMessage> ramiMessages =
                    ramiMessageRepository.findLatestActive(now, now.minusHours(SxSituationConverter.CLOSED_HOURS));
            messages = ramiMessages.size();
            final List<String> ids = ramiMessages.stream().map(m -> m.id).distinct().toList();
            final Map<String, ZonedDateTime> firstCreated = ids.isEmpty() ? Map.of()
                    : ramiMessageRepository.findFirstCreated(ids).stream()
                            .collect(Collectors.toMap(RamiFirstCreated::getId, RamiFirstCreated::getCreated));

            final JourneySource journeySource = publishedJourneys(ramiMessages);
            journeyProblem = journeySource.unavailableReason();
            final SxSituationConverter converter = new SxSituationConverter(
                    new InMemoryStationUicLookup(stationRepository.findAll()),
                    new SiriStopResolver(petiMatcher()),
                    new PublishedJourneyRefResolver(journeySource.journeys()));

            stage = Stage.BUILD;
            result = converter.convert(ramiMessages, firstCreated);
            final SxSituationMarshaller marshaller =
                    new SxSituationMarshaller(siriWritingService, NeTExIdGenerator.CODESPACE);
            final byte[] bytes = marshaller.marshalToBytes(marshaller.marshal(result.situations(), now));
            outputSize = bytes.length;

            stage = Stage.VALIDATE;
            if (!siriWritingService.isSchemaValid(bytes, "sx")) {
                throw new InvalidSiriOutputException("SIRI-SX output failed SIRI 2.0 XSD validation");
            }

            stage = Stage.PERSIST;
            stored = persist(bytes, now) ? "new" : "unchanged";
            lastUpdateService.update(LastUpdateService.LastUpdatedType.SIRI_SX_STATIONS);
            if (journeyProblem == null) {
                lastUpdateService.update(LastUpdateService.LastUpdatedType.SIRI_SX_TRAINS);
            }

            stage = Stage.COMPLETE;
            logEvent(result.dropped() > 0 || journeyProblem != null ? "partial" : "success", "NULL", stage, stopWatch,
                    messages, result, outputSize, stored, journeyProblem, null);
        } catch (final Exception e) {
            logEvent("error", e.getClass().getSimpleName(), stage, stopWatch, messages, result, outputSize, stored,
                    journeyProblem, e);
        }
    }

    private PetiUicMatcher petiMatcher() {
        final PetiUicMatcher matcher = petiStopSource.getMatcher();
        if (matcher.matchedCount() == 0) {
            throw new PetiUnavailableException("PETI stop snapshot is empty after warm-up");
        }
        return matcher;
    }

    /**
     * Published journeys for the departure dates of the train messages (they can be days ahead). When the NeTEx
     * journeys are missing, missing for today or tomorrow, or old, no journeys are returned: train messages are dropped and station messages are
     * still published.
     */
    private JourneySource publishedJourneys(final List<RamiMessage> ramiMessages) {
        final List<LocalDate> dates = ramiMessages.stream()
                .map(m -> m.trainDepartureDate)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (dates.isEmpty()) {
            return new JourneySource(Map.of(), null);
        }
        final Long version = publishedJourneyRepository.getMaxDatasetVersion();
        if (version == null) {
            return new JourneySource(Map.of(), PublishedJourneysUnavailableException.Reason.MISSING.name());
        }
        final List<NeTExPublishedJourney> journeys =
                publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(version, dates);
        // NeTEx publishes today and the next days; no journeys for such a date means the dataset is not usable.
        // Dates further ahead are not in NeTEx yet, so no journeys for them is normal.
        final LocalDate lastPublishedDate = DateProvider.dateInHelsinki().plusDays(1);
        final Set<LocalDate> datesWithJourneys = journeys.stream()
                .map(j -> j.trainId.departureDate)
                .collect(Collectors.toSet());
        if (dates.stream().anyMatch(d -> !d.isAfter(lastPublishedDate) && !datesWithJourneys.contains(d))) {
            return new JourneySource(Map.of(), PublishedJourneysUnavailableException.Reason.EMPTY.name());
        }
        final ZonedDateTime newest = journeys.stream()
                .map(j -> j.generatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (!journeys.isEmpty()
                && (newest == null || newest.isBefore(DateProvider.nowInHelsinki().minusHours(PublishedJourneysUnavailableException.MAX_AGE_HOURS)))) {
            return new JourneySource(Map.of(), PublishedJourneysUnavailableException.Reason.STALE.name());
        }
        final Map<TrainId, ResolvedJourney> byTrainId = new HashMap<>();
        for (final NeTExPublishedJourney j : journeys) {
            byTrainId.put(j.trainId, new ResolvedJourney(
                    new ServiceJourneyId(j.serviceJourneyId),
                    new DataFrameRef(j.trainId.departureDate.toString()),
                    new LineId(j.lineId),
                    j.operatorRef != null ? new OperatorRef(j.operatorRef) : null,
                    null));
        }
        return new JourneySource(byTrainId, null);
    }

    private record JourneySource(Map<TrainId, ResolvedJourney> journeys, String unavailableReason) {}

    /** @return true when a new file was stored, false when the situations were unchanged */
    private boolean persist(final byte[] bytes, final ZonedDateTime now) {
        final GeneratedExport last = generatedExportRepository.findFirstByFileNameOrderByIdDesc(FILE_NAME);
        if (last != null && withoutResponseTimestamps(last.data).equals(withoutResponseTimestamps(bytes))) {
            last.created = now;
            generatedExportRepository.flush();
            return false;
        }
        final GeneratedExport export = new GeneratedExport();
        export.data = bytes;
        export.created = now;
        export.fileName = FILE_NAME;
        generatedExportRepository.persist(List.of(export));
        return true;
    }

    private static String withoutResponseTimestamps(final byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8)
                .replaceAll("<ResponseTimestamp>[^<]*</ResponseTimestamp>", "");
    }

    private enum Stage { PREPARE, BUILD, VALIDATE, PERSIST, COMPLETE }

    private void logEvent(final String outcome, final String errorType, final Stage stage, final StopWatch stopWatch,
            final int messages, final SxResult result, final int outputSize, final String stored,
            final String journeyProblem, final Throwable thrown) {
        final String line = StringUtil.format(
                "event=rail.siri.generation operation=generateSiriSx outcome={} error.type={} stage={} duration={} "
                        + "rail.siri.service=sx rail.siri.sx.messages={} rail.siri.sx.situations={} "
                        + "rail.siri.sx.dropped.no_text={} rail.siri.sx.dropped.next_stop={} "
                        + "rail.siri.sx.dropped.no_stop_place={} rail.siri.sx.dropped.no_journey={} "
                        + "rail.siri.sx.unknown_stations={} rail.siri.sx.journeys_unavailable={} rail.siri.sx.stored={} "
                        + "rail.siri.output.size={}",
                outcome, errorType, stage.name().toLowerCase(Locale.ROOT),
                durationSeconds(stopWatch.getDuration().toMillis()), messages,
                result != null ? result.situations().size() : 0, result != null ? result.noText() : 0,
                result != null ? result.nextStop() : 0, result != null ? result.noStopPlace() : 0,
                result != null ? result.noJourneyIds().size() : 0, result != null ? result.unknownStations() : 0,
                journeyProblem != null ? journeyProblem : "NULL", stored, outputSize);
        switch (outcome) {
            case "success" -> log.info(line);
            case "partial" -> {
                log.warn(line);
                if (result != null && !result.noJourneyIds().isEmpty()) {
                    log.warn("event=rail.siri.sx.no_journey ids={}", result.noJourneyIds());
                }
            }
            default -> log.error(line, thrown);
        }
    }
}
