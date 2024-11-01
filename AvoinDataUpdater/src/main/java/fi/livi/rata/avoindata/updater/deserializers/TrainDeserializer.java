package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.dao.localization.TrainCategoryRepository;
import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;

@Component
public class TrainDeserializer extends AEntityDeserializer<Train> {
    private Logger logger = LoggerFactory.getLogger(TrainDeserializer.class);

    @Autowired
    private DateProvider dp;

    @Autowired
    private TrainCategoryRepository trainCategoryRepository;

    @Autowired
    private TrainTypeRepository trainTypeRepository;



    @Override
    public Train deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        final JsonNode id = node.get("id");
        final Long trainNumber = id.get("junanumero").asLong();
        final LocalDate departureDate = LocalDate.parse(id.get("lahtopvm").asText());

        final JsonNode timetable = node.get("aikataulu");
        final JsonNode operator = timetable.get("operaattori");

        final int operatorUICCode = operator.get("uicKoodi").asInt();
        final String operatorShortCode = operator.get("lyhenne").asText();

        final JsonNode junatyyppi = timetable.get("junatyyppi");
        final long trainCategoryId = junatyyppi.get("junalaji").get("id").asLong();
        final long trainTypeId = junatyyppi.get("id").asLong();

        final JsonNode lahiliikenteenLinjatunnusNode = timetable.get("lahiliikenteenLinjatunnus");
        final String commuterLineID = lahiliikenteenLinjatunnusNode != null ? lahiliikenteenLinjatunnusNode.get("nimi").asText() : "";
        final boolean cancelled = !"VOIMASSAOLEVA".equals(node.get("jupaTila").asText());

        final List<TimeTableRow> timeTableRows = deserializeTimeTableRows(jsonParser, node.get("jupaTapahtumas"));
        final List<TimeTableRow> sortedTimeTableRows = sortTimeTableRows(timeTableRows);

        final Long maxVersion = getMaxVersion(sortedTimeTableRows);

        final Train.TimetableType timetableType = timetable.get(
                "kiireellinenHakemus") != null ? Train.TimetableType.ADHOC : Train.TimetableType.REGULAR;
        final ZonedDateTime timetableAcceptanceDate = getNodeAsDateTime(timetable.get("hyvaksymisaika"));

        final boolean runningCurrently = !cancelled && isRunningCurrently(sortedTimeTableRows);
        final Train train = new Train(trainNumber, departureDate, operatorUICCode, operatorShortCode, trainCategoryId, trainTypeId,
                commuterLineID, runningCurrently, cancelled, maxVersion, timetableType, timetableAcceptanceDate);

        for (final TimeTableRow timeTableRow : timeTableRows) {
            timeTableRow.train = train;
        }

        emptyCommercialTrackInTimeTableRows(sortedTimeTableRows);

        initializeIsTrainStoppingInformation(sortedTimeTableRows);

        train.timeTableRows = sortedTimeTableRows;

        final Optional<TrainCategory> trainCategoryOptional = trainCategoryRepository.findByIdCached(trainCategoryId);
        final Optional<TrainType> trainTypeOptional = trainTypeRepository.findByIdCached(trainTypeId);
        train.trainCategory = trainCategoryOptional.isPresent() ? trainCategoryOptional.get().name : "Unknown";
        train.trainType = trainTypeOptional.isPresent() ? trainTypeOptional.get().name : "Unknown";

        return train;
    }

    private Long getMaxVersion(final List<TimeTableRow> sortedTimeTableRows) {
        Long maxVersion = Long.MIN_VALUE;
        for (final TimeTableRow sortedTimeTableRow : sortedTimeTableRows) {
            if (sortedTimeTableRow.version > maxVersion) {
                maxVersion = sortedTimeTableRow.version;
            }
        }

        for (final TimeTableRow sortedTimeTableRow : sortedTimeTableRows) {
            if (sortedTimeTableRow.causes != null && !sortedTimeTableRow.causes.isEmpty()) {
                for (final Cause cause : sortedTimeTableRow.causes) {
                    if (cause.version > maxVersion) {
                        maxVersion = cause.version;
                    }
                }
            }
        }
        return maxVersion;
    }

    private boolean isRunningCurrently(final List<TimeTableRow> sortedTimeTableRows) {
        Optional<TimeTableRow> firstTravelledRow = Optional.empty();
        Optional<TimeTableRow> lastNonCancelledRow = Optional.empty();
        for (final TimeTableRow sortedTimeTableRow : sortedTimeTableRows) {
            if (!firstTravelledRow.isPresent() && sortedTimeTableRow.actualTime != null && !sortedTimeTableRow.cancelled) {
                firstTravelledRow = Optional.of(sortedTimeTableRow);
            }

            if (!sortedTimeTableRow.cancelled) {
                lastNonCancelledRow = Optional.of(sortedTimeTableRow);
            }
        }

        final boolean isRunningCurrently = firstTravelledRow.isPresent() && lastNonCancelledRow.isPresent() && lastNonCancelledRow.get().actualTime == null;
        if (isRunningCurrently) {
            final boolean isOldTrain = dp.nowInHelsinki().minusDays(2).isAfter(lastNonCancelledRow.get().scheduledTime);
            if (isOldTrain) {
                logger.info("Returning isRunningCurrently = false for row {} ({})", lastNonCancelledRow.get().id,lastNonCancelledRow.get());
                return false;
            }
            else {
                return true;
            }
        } else {
            return false;
        }
    }

    private void initializeIsTrainStoppingInformation(final List<TimeTableRow> sortedTimeTableRows) {
        if (sortedTimeTableRows == null || sortedTimeTableRows.isEmpty()) {
            return;
        }

        for (int i = 0; i < sortedTimeTableRows.size() - 1; i++) {
            final TimeTableRow current = sortedTimeTableRows.get(i);
            final TimeTableRow next = sortedTimeTableRows.get(i + 1);

            if (current.station.stationUICCode == next.station.stationUICCode) {
                if (current.scheduledTime.isEqual(next.scheduledTime)) {
                    current.trainStopping = false;
                    current.commercialStop = null;
                    next.trainStopping = false;
                    next.commercialStop = null;
                } else {
                    current.commercialStop = next.commercialStop;
                }
            }
        }


        sortedTimeTableRows.get(0).commercialStop = true;
        sortedTimeTableRows.get(sortedTimeTableRows.size() - 1).commercialStop = true;
    }

    private List<TimeTableRow> sortTimeTableRows(final List<TimeTableRow> timeTableRows) {
        return timeTableRows.stream().sorted((o1, o2) -> {
            final int i = o1.scheduledTime.compareTo(o2.scheduledTime);
            if (i == 0) {
                return o1.type.compareTo(o2.type);
            } else {
                return i;
            }
        }).collect(Collectors.toList());
    }

    private void emptyCommercialTrackInTimeTableRows(final List<TimeTableRow> sortedTimeTableRows) {
        for (int i = 0; i < sortedTimeTableRows.size(); i++) {
            if (i == 0 || i == sortedTimeTableRows.size() - 1) {
                final TimeTableRow current = sortedTimeTableRows.get(i);
                if (current.cancelled) {
                    current.commercialTrack = "";
                }
            } else {
                final TimeTableRow current = sortedTimeTableRows.get(i);
                final TimeTableRow next = sortedTimeTableRows.get(i + 1);

                if (current.cancelled || current.train.cancelled) {
                    current.commercialTrack = "";
                } else if (shouldCommercialTrackBeEmptied(current, next)) {
                    current.commercialTrack = "";
                    next.commercialTrack = "";
                }
            }
        }
    }

    private boolean shouldCommercialTrackBeEmptied(final TimeTableRow current, final TimeTableRow next) {
        return current.type == TimeTableRow.TimeTableRowType.ARRIVAL && current.station.stationUICCode == next.station.stationUICCode &&
                !current.cancelled && !next.cancelled && current.scheduledTime.equals(next.scheduledTime);
    }

    private List<TimeTableRow> deserializeTimeTableRows(final JsonParser jsonParser, final JsonNode jupaTapahtumas) throws IOException {
        return Arrays.asList(jsonParser.getCodec().readValue(jupaTapahtumas.traverse(jsonParser.getCodec()), TimeTableRow[].class));
    }
}
