package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TrainReady;

@Component
public class TimeTableRowDeserializer extends AEntityDeserializer<TimeTableRow> {
    @Autowired
    private TrainReadyDeserializer trainReadyDeserializer;

    @Override
    public TimeTableRow deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final JsonNode liikennepaikka = node.get("liikennepaikka");

        final JsonNode pkId = node.get("id");
        final long attapId = pkId.get("id").asLong();
        final long trainNumber = pkId.get("junanumero").asLong();
        final long version = node.get("version").asLong();
        final LocalDate departureDate = getNodeAsLocalDate(pkId.get("lahtopvm"));

        final String stationShortCode = liikennepaikka.get("lyhenne").asText();
        final int stationcUICCode = liikennepaikka.get("uicKoodi").asInt();
        final String countryCode = liikennepaikka.get("maakoodi").asText();
        final TimeTableRow.TimeTableRowType type = "LAHTO".equals(
                node.get("tyyppi").asText()) ? TimeTableRow.TimeTableRowType.DEPARTURE : TimeTableRow.TimeTableRowType.ARRIVAL;

        final JsonNode raidetunnus = node.get("kaupallinenNro");
        final String commercialTrack = raidetunnus != null ? raidetunnus.asText() : "";

        final boolean cancelled = !"VOIMASSAOLEVA".equals(node.get("jupaTila").asText());
        final ZonedDateTime scheduledTime = getNodeAsDateTime(node.get("suunniteltuAika"));

        final Object[] estimateAndSource = parseEstimateTime(node);
        ZonedDateTime estimate = (ZonedDateTime) estimateAndSource[0];
        TimeTableRow.EstimateSourceEnum estimateSource = (TimeTableRow.EstimateSourceEnum) estimateAndSource[1];

        final JsonNode toteutunutAika = node.get("toteutunutAika");
        final ZonedDateTime actualTime = getNodeAsDateTime(toteutunutAika);

        final JsonNode kaupallinen = node.get("kaupallinen");
        final Boolean commercialStop = kaupallinen != null ? kaupallinen.booleanValue() : null;

        final Long differenceInMinutes = calculateScheduledDifference(scheduledTime,
                actualTime != null ? actualTime : (estimate != null ? estimate : null));

        final Set<TrainReady> trainReadies = new HashSet<>();
        final TrainReady trainReady = trainReadyDeserializer.deserialize(node);
        if (trainReady != null) {
            trainReadies.add(trainReady);
        }

        JsonNode aikatauluriviNode = node.get("aikataulutapahtuma").get("aikataulurivi");
        ZonedDateTime commercialTrackChanged = null;
        for (JsonNode raidemuutos : aikatauluriviNode.get("raidemuutos")) {
            ZonedDateTime luontiPvm = getNodeAsDateTime(raidemuutos.get("luontiPvm"));
            if (commercialTrackChanged == null || luontiPvm.isAfter(commercialTrackChanged)) {
                commercialTrackChanged = luontiPvm;
            }
        }


        final TimeTableRow timeTableRow = new TimeTableRow(stationShortCode, stationcUICCode, countryCode, type, commercialTrack, cancelled,
                scheduledTime, estimate, actualTime, differenceInMinutes, attapId, trainNumber, departureDate, commercialStop, version,
                trainReadies, estimateSource, commercialTrackChanged);

        if (trainReady != null) {
            trainReady.timeTableRow = timeTableRow;
        }

        final JsonNode syytietos = node.get("syytietos");
        if (syytietos != null && !syytietos.isNull() && syytietos.size() > 0) {
            final Set<Cause> causes = deserializeCauseRows(jsonParser, syytietos).stream().filter(s -> s != null).collect(Collectors.toSet());

            causes.stream().forEach(x -> x.timeTableRow = timeTableRow);
            timeTableRow.causes = causes;
        }

        return timeTableRow;
    }

    private static Set<Cause> deserializeCauseRows(final JsonParser jsonParser, final JsonNode syytietos) throws IOException {
        return new HashSet<>(Arrays.asList(jsonParser.getCodec().readValue(syytietos.traverse(jsonParser.getCodec()), Cause[].class)));
    }

    @Nullable
    public static Long calculateScheduledDifference(final ZonedDateTime scheduledTime, final ZonedDateTime currentLiveTime) {
        //noinspection ReturnOfNull
        return (currentLiveTime == null) ? null : calculateDifference(currentLiveTime, scheduledTime);
    }

    public static long calculateDifference(@NonNull final ZonedDateTime currentLiveTime, @NonNull final ZonedDateTime scheduledTime) {
        final long millis = currentLiveTime.toInstant().toEpochMilli() - scheduledTime.toInstant().toEpochMilli();

        if (millis < 0) {
            return (millis - 29999) / (1000 * 60);
        } else {
            return (millis + 30000) / (1000 * 60);
        }
    }

    @Nullable
    public Object[] parseEstimateTime(final JsonNode node) {
        final JsonNode kasiennusteAika = node.get("kasiennusteAika");
        final JsonNode automaattiennusteAika = node.get("automaattiennusteAika");

        if (kasiennusteAika != null) {
            return new Object[]{getNodeAsDateTime(kasiennusteAika), TimeTableRow.EstimateSourceEnum.LIIKE_USER};

        } else if (automaattiennusteAika != null) {
            return new Object[]{getNodeAsDateTime(automaattiennusteAika), TimeTableRow.EstimateSourceEnum.LIIKE_AUTOMATIC};
        }

        return new Object[]{null, null};
    }
}
