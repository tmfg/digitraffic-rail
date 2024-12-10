package fi.livi.rata.avoindata.updater.deserializers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
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
    public ArrayList<KokoonpanoDto> filterNewestVersions(final KokoonpanoDto[] kokoonpanot) {
        final Map<String, KokoonpanoDto> kokoonpanotFiltered = Arrays.stream(kokoonpanot)
                .collect(Collectors.toMap(
                        (k) -> StringUtil.format("{}-{}", k.getJunanumero(), k.getLahtopaiva().toString()),
                        Function.identity(),
                        (kokoonpano1, kokoonpano2) -> {
                            final KokoonpanoDto current =
                                    kokoonpano1.getMessageDateTime().isAfter(kokoonpano2.getMessageDateTime()) ? kokoonpano1 : kokoonpano2;
                            log.debug("method=filterNewestVersions Found duplicate versions for train trainNumber={} departureDate={} with messageDateTimes {} vs {} selecting version {}",
                                      kokoonpano1.getJunanumero(), kokoonpano1.getLahtopaiva(), kokoonpano1.getMessageDateTime(), kokoonpano2.getMessageDateTime(), current.getMessageDateTime());
                            return current;
                        }
                ));
        return new ArrayList<>(kokoonpanotFiltered.values());
    }


    public List<JourneyComposition> transformToJourneyCompositions(final List<KokoonpanoDto> kokoonpanot) {
        final long version = Instant.now().toEpochMilli();
        return kokoonpanot.stream()
                .map(kokoonpanoDto -> transformToJourneyCompositions(kokoonpanoDto, version))
                .flatMap(Collection::stream)
                .toList();
    }

    private List<JourneyComposition> transformToJourneyCompositions(final KokoonpanoDto kokoonpano, final long version) {
        final List<JourneyComposition> compositions =
                kokoonpano.getOsavalit().stream().map(ov -> transformToJourneyComposition(kokoonpano, ov, version)).toList();
        final long countNoStartStation = compositions.stream().filter(journeyComposition -> journeyComposition.startStation == null).count();
        final long countNoEndStation = compositions.stream().filter(journeyComposition -> journeyComposition.endStation == null).count();
        if (countNoStartStation > 0 || countNoEndStation > 0) {
            log.error("method=transformToJourneyCompositions JourneyComposition for trainNumber={} on departureDate={} had empty start or end stations countNoStartStation={} countNoEndStation={} of compositions={}  messageDateTime={} compositions: returning zero compositions",
                    kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva(), countNoStartStation, countNoEndStation, compositions.size(), compositions.isEmpty() ? null : compositions.getFirst().messageDateTime);
            return Collections.emptyList();
        } else {
            log.info("method=transformToJourneyCompositions JourneyComposition for trainNumber={} on departureDate={} was OK for compositions={} messageDateTime={}", kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva(), compositions.size(), compositions.isEmpty() ? null : compositions.getFirst().messageDateTime);
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

        if (startStation == null) {
            log.error("method=transformToJourneyComposition startStation not found with oid={} or uicCode={} for trainNumber={} on departureDate={}", osavali.getAlkuAikataulupaikka(), osavali.getAlkuUICKoodi(), kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva());
        }
        final Optional<SimpleTimeTableRow> startTimetable =
                startStation != null ?
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
            log.error("method=transformToJourneyComposition startTimetable not found with oid={} or uicCode={} for trainNumber={} on departureDate={}", osavali.getAlkuAikataulupaikka(), osavali.getAlkuUICKoodi(), kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva());
        }

        final Station endStation = getStationByTrafficLocationOid(osavali.getLoppuAikataulupaikka(), osavali.getLoppuUICKoodi());
        if (endStation == null) {
            log.error("method=transformToJourneyComposition endStation not found with oid={} or uicCode={} for trainNumber={} on departureDate={}", osavali.getLoppuAikataulupaikka(), osavali.getLoppuUICKoodi(), kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva());
        }
        final Optional<SimpleTimeTableRow> endTimetable =
                endStation != null ?
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
            log.error("method=transformToJourneyComposition endTimetable not found with oid={} or uicCode={} for trainNumber={} on departureDate={}", osavali.getLoppuAikataulupaikka(), osavali.getLoppuUICKoodi(), kokoonpano.getJunanumero(), kokoonpano.getLahtopaiva());
        }
        return new JourneyComposition(
                operator,
                kokoonpano.getJunanumero().longValue(), // trainNumber
                kokoonpano.getLahtopaiva(), // LocalDate departureDate,
                trainType.isPresent() ? trainType.get().trainCategory.id : 0, // trainCategoryId
                trainType.isPresent() ? trainType.get().id : 0, // trainTypeId
                osavali.getKokonaispituus().intValue()/1000, // totalLength (convert from mm to m)
                osavali.getMaxNopeus(), // maximumSpeed

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
            log.error("method=getTimetableRow TimetableRow not found for trainNumber={} departureDate={} time={}, stationShortCode={}, type={} ({})",
                      junanumero, lahtopaiva, aika.toZonedDateTime(), stationShortCode, type, type.ordinal());
        }
        return result;
    }

    private static JourneyCompositionRow createJourneyCompositionRow(final TimeTableRow.TimeTableRowType type, final OffsetDateTime scheduledTime, final Station location) {
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
            log.error("method=getStationByTrafficLocationOid could not find station by oid={} with shortCode={}", stationOid, liikennepaikka.get().get("lyhenne").asText());
        }
        if (uicCodeFallback != null) {
            final Optional<Station> station = stationRepository.findByUicCode(uicCodeFallback);
            if (station.isPresent()) {
                return station.get();
            }
        }
        log.error("method=getStationByTrafficLocationOid could not find station by oid={} or uicCode={}", stationOid, uicCodeFallback);
        return null;
    }

    @Scheduled(fixedRate = 1000*60)
    public void logStatistics() {
        final long totalTime = took.getAndSet(0);
        final long totalCount = count.getAndSet(0);
        final long totalMatch = match.getAndSet(0);
        if (totalCount > 0) {
            log.info("method=logStatistics statistics=timeTableRowRepository.findSimpleBy tookMs={} ms, found / queries = {} / {} timePerQuery = {} ms", totalTime,
                    totalMatch, totalCount, String.format("%.2f", (double) totalTime / (double) totalCount));
        }
    }
}


