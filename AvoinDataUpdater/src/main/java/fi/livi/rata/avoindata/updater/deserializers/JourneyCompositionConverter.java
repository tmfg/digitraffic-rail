package fi.livi.rata.avoindata.updater.deserializers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import fi.finrail.koju.model.KokoonpanoDto;
import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.dao.metadata.OperatorRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.composition.JourneyCompositionRow;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;
import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;

/**
 * Composition for one leg of journey
 */
@Component
public class JourneyCompositionConverter {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final WagonDeserializer wagonDeserializer;
    private final LocomotiveDeserializer locomotiveDeserializer;
    private final OperatorRepository operatorRepository;
    private final TrainTypeRepository trainTypeRepository;
    private final TimeTableRowRepository timeTableRowRepository;
    private final StationRepository stationRepository;
    private final TrakediaLiikennepaikkaService trakediaLiikennepaikkaService;

    private final AtomicLong took = new AtomicLong(0);
    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong match = new AtomicLong(0);

    public JourneyCompositionConverter(final WagonDeserializer wagonDeserializer,
                                       final LocomotiveDeserializer locomotiveDeserializer,
                                       final OperatorRepository operatorRepository,
                                       final TrainTypeRepository trainTypeRepository,
                                       final TimeTableRowRepository timeTableRowRepository,
                                       final StationRepository stationRepository,
                                       final TrakediaLiikennepaikkaService trakediaLiikennepaikkaService) {
        this.wagonDeserializer = wagonDeserializer;
        this.locomotiveDeserializer = locomotiveDeserializer;
        this.operatorRepository = operatorRepository;
        this.trainTypeRepository = trainTypeRepository;
        this.timeTableRowRepository = timeTableRowRepository;
        this.stationRepository = stationRepository;
        this.trakediaLiikennepaikkaService = trakediaLiikennepaikkaService;
    }

    /**
     * Method returns only newest composition version per train and filters out old versions.
     *
     * @param kokoonpanot Might contain multiple composition versions for same trains
     * @return Newest compositions for individual trains
     */
    public ArrayList<KokoonpanoDto> filterNewestVersions(final List<KokoonpanoDto> kokoonpanot) {
        final Map<String, KokoonpanoDto> kokoonpanotFiltered = kokoonpanot.stream()
                .collect(Collectors.toMap(
                        JourneyCompositionConverter::getKeyForKokoonpano,
                        Function.identity(),
                        (kokoonpano1, kokoonpano2) -> {
                            final KokoonpanoDto current =
                                    kokoonpano1.getMessageDateTime().isAfter(kokoonpano2.getMessageDateTime()) ? kokoonpano1 : kokoonpano2;
                            log.debug(
                                    "method=filterNewestVersions Found duplicate versions for train trainNumber={} departureDate={} with messageDateTimes {} vs {} selecting version {}",
                                    kokoonpano1.getJunanumero(), kokoonpano1.getLahtopaiva(), kokoonpano1.getMessageDateTime(),
                                    kokoonpano2.getMessageDateTime(), current.getMessageDateTime());
                            return current;
                        }
                ));
        return new ArrayList<>(kokoonpanotFiltered.values());
    }

    final AtomicLong compositionVersionHolder = new AtomicLong(0);
    /**
     * Returns list of successfull transformations to JourneyComposition and failed kokoonpanot
     */
    public Pair<List<JourneyComposition>, List<KokoonpanoDto>> transformToJourneyCompositions(final List<KokoonpanoDto> kokoonpanot) {
        // Generate new version for compositions form curren time
        final long newVersion = Instant.now().toEpochMilli();
        if (newVersion <= compositionVersionHolder.get()) {
            // If new version is same or smaller (should not happen) as on previous run then increment it by 1 ms
            compositionVersionHolder.addAndGet(1L);
            log.warn("method=transformToJourneyCompositions Version was same as on previous run incrementing to version={} epoc ms", compositionVersionHolder.get());
        } else {
            compositionVersionHolder.set(newVersion);
        }

        final List<JourneyComposition> success = new ArrayList<>(kokoonpanot.size());
        final List<KokoonpanoDto> failed = new ArrayList<>();
        final Pair<List<JourneyComposition>, List<KokoonpanoDto>> result = Pair.of(success, failed);
        final AtomicInteger compositionsCount = new AtomicInteger(0);
        kokoonpanot.forEach(kokoonpanoDto -> {
            compositionsCount.addAndGet(1);
            // If creating version with over 1000 compositions then increment version by 1 ms to keep composition version max size in 1000.
            if (shouldIncrementVersion(compositionsCount.get())) {
                log.warn("method=transformToJourneyCompositions count={} is over 1000 compositions for version={} epoc ms, increasing by 1 ms", compositionsCount.get(), compositionVersionHolder.get());
                compositionVersionHolder.addAndGet(1L);
            }
            try {
                final List<JourneyComposition> transformationResult = transformToJourneyCompositions(kokoonpanoDto, compositionVersionHolder.get());
                success.addAll(transformationResult);
            } catch (final CompositionFailedException e) {
                failed.add(kokoonpanoDto);
            }
        });
        return result;
    }

    public static boolean shouldIncrementVersion(final int compositionsCount) {
        // 1001, 2001, 3001, ...
        return compositionsCount > 1000 && (compositionsCount-1) % 1000 == 0;
    }

    /**
     * Transforms KokoonpanoDto to list of JourneyCompositions. If composition cannot be formed because not all data is available
     * then CompositionFailedException is thrown.
     *
     * @param kokoonpano to be transformed
     * @param version    version for composition
     * @return composition with needed data to be saved to db
     * @throws CompositionFailedException if not all data is available
     */
    private List<JourneyComposition> transformToJourneyCompositions(final KokoonpanoDto kokoonpano, final long version)
            throws CompositionFailedException {
        final List<JourneyComposition> compositions =
                kokoonpano.getOsavalit().stream().map(ov -> transformToJourneyComposition(kokoonpano, ov, version)).toList();
        final long countNoStartStation = compositions.stream().filter(journeyComposition -> journeyComposition.startStation == null).count();
        final long countNoEndStation = compositions.stream().filter(journeyComposition -> journeyComposition.endStation == null).count();
        if (countNoStartStation > 0 || countNoEndStation > 0) {
            final String msg = StringUtil.format(
                    "method=transformToJourneyCompositions JourneyComposition for trainNumber={} on departureDate={} had empty start or end stations countNoStartStation={} countNoEndStation={} of compositions={}  messageDateTime={} compositions: returning zero compositions",
                    kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva(), countNoStartStation, countNoEndStation, compositions.size(),
                    compositions.getFirst().messageDateTime);
            throw new CompositionFailedException(kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva(), msg);
        } else {
            log.info(
                    "method=transformToJourneyCompositions JourneyComposition for trainNumber={} on departureDate={} was OK for compositions={} messageDateTime={}",
                    kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva(), compositions.size(),
                    compositions.isEmpty() ? null : compositions.getFirst().messageDateTime);
        }
        return compositions;
    }

    private JourneyComposition transformToJourneyComposition(final KokoonpanoDto kokoonpano,
                                                             final fi.finrail.koju.model.OsavaliDto osavali,
                                                             final long version) {

        final fi.livi.rata.avoindata.common.domain.metadata.Operator o =
                kokoonpano.getSender() != null ?
                operatorRepository.findByOperatorUICCodeCached(kokoonpano.getSender()) : null;
        // kokoonpano.getSender() == operatorShortCode
        final Operator operator = o != null ?
                                  new Operator(kokoonpano.getSender() /* UICCode */, o.operatorShortCode) : null;

        final Collection<Wagon> wagons =
                osavali.getVaunut().stream().map(v -> wagonDeserializer.transformToWagon(v, kokoonpano.getJunanumero())).toList();

        final Collection<Locomotive> locomotives =
                osavali.getVeturit().stream().map(l -> locomotiveDeserializer.transformToLocomotive(l, kokoonpano.getJunanumero())).toList();

        final Optional<TrainType> trainType = osavali.getCategory() != null ?  // == TrainType.name
                                              trainTypeRepository.findFirstByNameCached(osavali.getCategory()) : Optional.empty();

        // JRI -> {ArrayNode@25383} "[{"tunniste":"1.2.246.586.1.39.81517","virallinenSijainti":[496612,6718700],"lyhenne":"Jri","nimiSe":null,"nimiEn":null}]"
        final Station startStation = getStationByTrafficLocationOid(osavali.getAlkuAikataulupaikka(), osavali.getAlkuUICKoodi());

        if (startStation == null || osavali.getAlkuaika() == null) {
            log.warn("method=transformToJourneyComposition startStation not found with oid={} or uicCode={} for trainNumber={} on departureDate={}",
                    osavali.getAlkuAikataulupaikka(), osavali.getAlkuUICKoodi(), kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva());
        }
        final Optional<SimpleTimeTableRow> startTimetable =
                startStation != null && osavali.getAlkuaika() != null ?
                getTimetableRow(
                        kokoonpano.getJunanumero(),
                        kokoonpano.getLahtopaiva(),
                        osavali.getAlkuaika(),
                        startStation.shortCode,
                        TimeTableRow.TimeTableRowType.DEPARTURE) : Optional.empty();

        final JourneyCompositionRow startStationRow =
                startTimetable.isPresent() ?
                createJourneyCompositionRow(TimeTableRow.TimeTableRowType.DEPARTURE, osavali.getAlkuaika(), startStation) : null;
        if (startStation != null && startTimetable.isEmpty()) {
            log.warn(
                    "method=transformToJourneyComposition startTimetable not found with oid={} or uicCode={} for trainNumber={} on departureDate={}",
                    osavali.getAlkuAikataulupaikka(), osavali.getAlkuUICKoodi(), kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva());
        }

        final Station endStation = getStationByTrafficLocationOid(osavali.getLoppuAikataulupaikka(), osavali.getLoppuUICKoodi());
        if (endStation == null || osavali.getLoppuaika() == null) {
            log.warn(
                    "method=transformToJourneyComposition endStation not found with oid={} or uicCode={} for trainNumber={} on departureDate={} endTime={}",
                    osavali.getLoppuAikataulupaikka(), osavali.getLoppuUICKoodi(), kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva(),
                    osavali.getLoppuaika());
        }
        final Optional<SimpleTimeTableRow> endTimetable =
                endStation != null && osavali.getLoppuaika() != null ?
                getTimetableRow(
                        kokoonpano.getJunanumero(),
                        kokoonpano.getLahtopaiva(),
                        osavali.getLoppuaika(),
                        endStation.shortCode,
                        TimeTableRow.TimeTableRowType.ARRIVAL) : Optional.empty();

        final JourneyCompositionRow endStationRow =
                endTimetable.isPresent() ?
                createJourneyCompositionRow(TimeTableRow.TimeTableRowType.ARRIVAL, osavali.getLoppuaika(), endStation) : null;
        if (endStation != null && endTimetable.isEmpty()) {
            log.warn("method=transformToJourneyComposition endTimetable not found with oid={} or uicCode={} for trainNumber={} on departureDate={}",
                    osavali.getLoppuAikataulupaikka(), osavali.getLoppuUICKoodi(), kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva());
        }
        return new JourneyComposition(
                operator,
                kokoonpano.getJunanumero().longValue(), // trainNumber
                kokoonpano.getLahtopaiva(), // LocalDate departureDate,
                trainType.isPresent() ? trainType.get().trainCategory.id : 0, // trainCategoryId
                trainType.isPresent() ? trainType.get().id : 0, // trainTypeId
                Objects.requireNonNullElse(osavali.getKokonaispituus(), 0).intValue() / 1000, // totalLength (convert from mm to m)
                Objects.requireNonNullElse(osavali.getMaxNopeus(), 0), // maximumSpeed

                version, // version,
                kokoonpano.getMessageDateTime().toInstant(), // messageDateTime from koju api

                wagons, // Collection<Wagon> wagons
                locomotives, // Collection<Locomotive> locomotives
                startStationRow, // JourneyCompositionRow startStation,
                endStationRow, // JourneyCompositionRow endStation

                startTimetable.map(SimpleTimeTableRow::getAttapId).orElse(null), // attapId,
                endTimetable.map(SimpleTimeTableRow::getAttapId).orElse(null) // saapAttapId
        );
    }

    private Optional<SimpleTimeTableRow> getTimetableRow(final int junanumero,
                                                         final LocalDate lahtopaiva,
                                                         final OffsetDateTime aika,
                                                         final String stationShortCode,
                                                         final TimeTableRow.TimeTableRowType type) {
        final StopWatch start = StopWatch.createStarted();
        final Optional<SimpleTimeTableRow> result = timeTableRowRepository.findSimpleBy(
                junanumero,
                lahtopaiva,
                aika.toZonedDateTime(),
                stationShortCode,
                type);
        took.addAndGet(start.getDuration().toMillis());
        count.addAndGet(1);
        match.addAndGet(result.isPresent() ? 1 : 0);
        if (result.isEmpty()) {
            log.warn("method=getTimetableRow TimetableRow not found for trainNumber={} departureDate={} time={}, stationShortCode={}, type={} ({})",
                    junanumero, lahtopaiva, aika.toZonedDateTime(), stationShortCode, type, type.ordinal());
        }
        return result;
    }

    private static JourneyCompositionRow createJourneyCompositionRow(final TimeTableRow.TimeTableRowType type, final OffsetDateTime scheduledTime,
                                                                     final Station location) {
        final ZonedDateTime scheduledTimeUtc = scheduledTime.toZonedDateTime().withZoneSameInstant(ZoneId.of("Z"));
        return new JourneyCompositionRow(scheduledTimeUtc, location.shortCode, location.uicCode, location.countryCode, type);
    }

    private Station getStationByTrafficLocationOid(final String stationOid, final Integer uicCodeFallback) {
        // JRI -> {ArrayNode@25383} "[{"tunniste":"1.2.245.578.9.01.23456","virallinenSijainti":[496612,6718700],"lyhenne":"Jri","nimiSe":null,"nimiEn":null}]"
        final Optional<JsonNode> liikennepaikka =
                trakediaLiikennepaikkaService.getTrakediaLiikennepaikkaNodes().values().stream().map(arrayNode -> arrayNode.get(0))
                        .filter(jsonNode -> jsonNode.get("tunniste").asText().equals(stationOid)).findFirst();
        if (liikennepaikka.isPresent()) {
            final Station station = stationRepository.findByShortCodeIgnoreCase(liikennepaikka.get().get("lyhenne").asText());
            if (station != null) {
                return station;
            }
        }
        if (uicCodeFallback != null) {
            final Optional<Station> station = stationRepository.findByUicCode(uicCodeFallback);
            if (station.isPresent()) {
                return station.get();
            }
        }
        log.warn("method=getStationByTrafficLocationOid could not find station by oid={} and shortCode={} or uicCode={}", stationOid,
                liikennepaikka.isPresent() ? liikennepaikka.get().get("lyhenne").asText() : "null", uicCodeFallback);
        return null;
    }

    @Scheduled(fixedRate = 1000 * 60)
    public void logStatistics() {
        final long totalTime = took.getAndSet(0);
        final long totalCount = count.getAndSet(0);
        final long totalMatch = match.getAndSet(0);
        if (totalCount > 0) {
            log.info(
                    "method=logStatistics statistics=timeTableRowRepository.findSimpleBy tookMs={} ms, found / queries = {} / {} timePerQuery = {} ms",
                    totalTime,
                    totalMatch, totalCount, String.format("%.2f", (double) totalTime / (double) totalCount));
        }
    }

    public static class CompositionFailedException extends Exception {
        private final Integer junanumero;
        private final LocalDate lahtopaiva;
        private final String msg;

        public CompositionFailedException(final Integer junanumero, final LocalDate lahtopaiva, final String msg) {
            this.junanumero = junanumero;
            this.lahtopaiva = lahtopaiva;
            this.msg = msg;
        }

        public Integer getJunanumero() {
            return junanumero;
        }

        public LocalDate getLahtopaiva() {
            return lahtopaiva;
        }

        public String getMsg() {
            return msg;
        }
    }

    /**
     * Returns key to be used in maps for kokoonpano. Key is formed as "departureDate-trainNumber"
     *
     * @param kokoonpano to get key for
     * @return "departureDate-trainNumber"
     */
    public static String getKeyForKokoonpano(final KokoonpanoDto kokoonpano) {
        return StringUtil.format("{}-{}", kokoonpano.getLahtopaiva().toString(), kokoonpano.getJunanumero());
    }
}


