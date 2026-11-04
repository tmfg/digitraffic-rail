package fi.livi.rata.avoindata.updater.service.siri.sx;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import fi.livi.rata.avoindata.updater.service.siri.common.SiriFeed;
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
        int ramiMessageCount = 0;
        SxResult conversionResult = null;
        int outputSize = 0;
        String storeResult = "NULL";
        String journeysUnavailableReason = null;
        try {
            final ZonedDateTime now = DateProvider.nowInHelsinki();
            final List<RamiMessage> ramiMessages = ramiMessageRepository.findLatestActive(
                    now.toInstant(), now.toInstant().minus(SxSituationConverter.CLOSED_HOURS, ChronoUnit.HOURS));
            ramiMessageCount = ramiMessages.size();

            final JourneySource journeySource = publishedJourneys(ramiMessages);
            journeysUnavailableReason = journeySource.unavailableReason();

            stage = Stage.BUILD;
            conversionResult = convert(ramiMessages, journeySource);

            final byte[] bytes = marshal(conversionResult, now);
            outputSize = bytes.length;

            stage = Stage.VALIDATE;
            if (!siriWritingService.isSchemaValid(bytes, SiriFeed.SX)) {
                throw new InvalidSiriOutputException("SIRI-SX output failed SIRI 2.0 XSD validation");
            }

            stage = Stage.PERSIST;
            storeResult = persist(bytes, now) ? "new" : "unchanged";
            lastUpdateService.update(LastUpdateService.LastUpdatedType.SIRI_SX_STATIONS);
            if (journeysUnavailableReason == null) {
                lastUpdateService.update(LastUpdateService.LastUpdatedType.SIRI_SX_TRAINS);
            }

            stage = Stage.COMPLETE;
            logEvent(conversionResult.dropped() > 0 || journeysUnavailableReason != null ? "partial" : "success", "NULL", stage, stopWatch,
                    ramiMessageCount, conversionResult, outputSize, storeResult, journeysUnavailableReason, null);
        } catch (final Exception e) {
            logEvent("error", e.getClass().getSimpleName(), stage, stopWatch, ramiMessageCount, conversionResult, outputSize, storeResult,
                    journeysUnavailableReason, e);
        }
    }

    private SxResult convert(final List<RamiMessage> ramiMessages, final JourneySource journeySource) {
        final List<String> ramiMessageIds = ramiMessages.stream().map(m -> m.id).distinct().toList();
        final Map<String, Instant> firstCreated = ramiMessageIds.isEmpty() ? Map.of()
                : ramiMessageRepository.findFirstCreated(ramiMessageIds).stream()
                .collect(Collectors.toMap(RamiFirstCreated::getId, RamiFirstCreated::getCreated));
        return new SxSituationConverter(
                new InMemoryStationUicLookup(stationRepository.findAll()),
                new SiriStopResolver(petiMatcher()),
                new PublishedJourneyRefResolver(journeySource.journeys()))
                .convert(ramiMessages, firstCreated);
    }

    private byte[] marshal(final SxResult conversionResult, final ZonedDateTime now) {
        final SxSituationMarshaller marshaller =
                new SxSituationMarshaller(siriWritingService, NeTExIdGenerator.CODESPACE);
        return marshaller.marshalToBytes(marshaller.marshal(conversionResult.situations(), now));
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
     * journeys are missing, missing for today or tomorrow (when a message needs them), or old, no journeys are
     * returned: train messages are dropped and station messages are still published.
     */
    private JourneySource publishedJourneys(final List<RamiMessage> ramiMessages) {
        final List<LocalDate> departureDates = ramiMessages.stream()
                .map(m -> m.trainDepartureDate)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (departureDates.isEmpty()) {
            return new JourneySource(Map.of(), null);
        }
        final Long version = publishedJourneyRepository.getMaxDatasetVersion();
        if (version == null) {
            return new JourneySource(Map.of(), PublishedJourneysUnavailableException.Reason.MISSING.name());
        }
        final List<NeTExPublishedJourney> journeys =
                publishedJourneyRepository.findByDatasetVersionAndDepartureDates(version, departureDates);
        // Train departure dates are Finnish dates, like the stored NeTEx window. The window always has today and
        // tomorrow, so journeys must be found for those dates. Older and later dates may be outside the stored
        // window, so a message for them is simply dropped when it has no journey.
        if (isJourneyMissingForTodayOrTomorrow(departureDates, journeys)) {
            return new JourneySource(Map.of(), PublishedJourneysUnavailableException.Reason.EMPTY.name());
        }
        final Instant newest = journeys.stream()
                .map(j -> j.generatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (!journeys.isEmpty()
                && (newest == null || newest.isBefore(Instant.now().minus(PublishedJourneysUnavailableException.MAX_AGE_HOURS, ChronoUnit.HOURS)))) {
            return new JourneySource(Map.of(), PublishedJourneysUnavailableException.Reason.STALE.name());
        }
        final Map<TrainId, ResolvedJourney> byTrainId = journeys.stream()
                .collect(Collectors.toMap(j -> j.trainId, SiriSxGenerationService::toResolvedJourney, (_, b) -> b));
        return new JourneySource(byTrainId, null);
    }

    private static ResolvedJourney toResolvedJourney(final NeTExPublishedJourney j) {
        return new ResolvedJourney(
                new ServiceJourneyId(j.serviceJourneyId),
                new DataFrameRef(j.trainId.departureDate.toString()),
                new LineId(j.lineId),
                j.operatorRef != null ? new OperatorRef(j.operatorRef) : null,
                null);
    }

    private static boolean isJourneyMissingForTodayOrTomorrow(final List<LocalDate> messageDates,
                                                              final List<NeTExPublishedJourney> journeys) {
        final LocalDate today = DateProvider.dateInHelsinki();
        final Set<LocalDate> datesWithJourneys = journeys.stream()
                .map(j -> j.trainId.departureDate)
                .collect(Collectors.toSet());
        // The stored NeTEx window always has journeys for today and tomorrow, so a message date in that range without
        // any journeys means the NeTEx data is missing. Older and later dates may be outside the stored window, so
        // those are not checked.
        return messageDates.stream()
                .filter(d -> !d.isBefore(today) && !d.isAfter(today.plusDays(1)))
                .anyMatch(d -> !datesWithJourneys.contains(d));
    }

    private record JourneySource(Map<TrainId, ResolvedJourney> journeys, String unavailableReason) {
    }

    /**
     * @return true when a new file was stored, false when the situations were unchanged
     */
    private boolean persist(final byte[] bytes, final ZonedDateTime now) {
        final GeneratedExport last = generatedExportRepository.findFirstByFileNameOrderByIdDesc(FILE_NAME);
        // If last data is equal to current, update the creation time of the last stored file instead of storing a new file.
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

    private enum Stage {PREPARE, BUILD, VALIDATE, PERSIST, COMPLETE}

    private void logEvent(final String outcome, final String errorType, final Stage stage, final StopWatch stopWatch,
                          final int ramiMessageCount, final SxResult conversionResult, final int outputSize, final String storeResult,
                          final String journeysUnavailableReason, final Throwable thrown) {
        final boolean converted = conversionResult != null;
        final LoggingEventBuilder event = switch (outcome) {
            case "success" -> log.atInfo();
            case "partial" -> log.atWarn();
            default -> log.atError();
        };
        event.addKeyValue("event", "rail.siri.generation")
                .addKeyValue("operation", "generateSiriSx")
                .addKeyValue("outcome", outcome)
                .addKeyValue("error.type", errorType)
                .addKeyValue("stage", stage.name().toLowerCase(Locale.ROOT))
                .addKeyValue("duration", durationSeconds(stopWatch.getDuration().toMillis()))
                .addKeyValue("rail.siri.service", SiriFeed.SX.logName())
                .addKeyValue("rail.siri.sx.messages", ramiMessageCount)
                .addKeyValue("rail.siri.sx.situations", converted ? conversionResult.situations().size() : 0)
                .addKeyValue("rail.siri.sx.dropped.no_text", converted ? conversionResult.noText() : 0)
                .addKeyValue("rail.siri.sx.dropped.next_stop", converted ? conversionResult.nextStop() : 0)
                .addKeyValue("rail.siri.sx.dropped.no_stop_place", converted ? conversionResult.noStopPlace() : 0)
                .addKeyValue("rail.siri.sx.dropped.no_journey", converted ? conversionResult.noJourneyIds().size() : 0)
                .addKeyValue("rail.siri.sx.unknown_stations", converted ? conversionResult.unknownStations() : 0)
                .addKeyValue("rail.siri.sx.journeys_unavailable",
                        journeysUnavailableReason != null ? journeysUnavailableReason : "NULL")
                .addKeyValue("rail.siri.sx.stored", storeResult)
                .addKeyValue("rail.siri.output.size", outputSize)
                .setCause(thrown)
                .log("SIRI-SX generation");
        if ("partial".equals(outcome) && converted && !conversionResult.noJourneyIds().isEmpty()) {
            log.atWarn()
                    .addKeyValue("event", "rail.siri.sx.no_journey")
                    .addKeyValue("ids", conversionResult.noJourneyIds())
                    .log("SIRI-SX messages without a journey");
        }
    }
}
