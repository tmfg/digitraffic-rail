package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.composition.JourneyCompositionRow;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Composition for one leg of journey
 */
@Component
public class JourneyCompositionDeserializer extends JsonDeserializer<JourneyComposition> {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    public JourneyComposition deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException {

        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        final long version = node.get("version").asLong();

        final JsonNode aikataulu = node.get("aikataulu");
        final Operator operator = new Operator();
        operator.operatorShortCode = aikataulu.get("operaattori").get("lyhenne").asText();
        operator.operatorUICCode = aikataulu.get("operaattori").get("uicKoodi").asInt();
        final Long trainNumber = aikataulu.get("aikataulunJunanumero").get("junanumero").asLong();

        final LocalDate departureDate = LocalDate.parse(node.get("lahtoPvm").asText());

        final JsonNode junatyyppi = aikataulu.get("junatyyppi");
        final long trainCategoryId = junatyyppi.get("junalaji").get("id").asLong();

        final long trainTypeId = junatyyppi.get("id").asLong();

        final long id = node.get("id").asLong();

        final int totalLength = node.get("kokonaispituus").asInt();
        final int maximumSpeed = node.get("jarrupainonopeus").asInt();

        final JourneyCompositionRow journeyCompositionStartStation = createJourneyCompositionRow(node.get("aikataulutapahtuma"));
        final JourneyCompositionRow journeyCompositionEndStation;
        if (node.get("viimeinenAikataulutapahtuma") != null) {
            journeyCompositionEndStation = createJourneyCompositionRow(node.get("viimeinenAikataulutapahtuma"));
        } else {
            log.error(String.format("JourneyCompositionRow is null, trainNumber %s departureDate %s", trainNumber, departureDate));
            journeyCompositionEndStation = null;
        }

        final JsonNode vaunus = node.get("vaunus");
        Collection<Wagon> wagons = new ArrayList<>();
        if (vaunus != null) {
            wagons = Arrays.asList(jsonParser.getCodec().readValue(vaunus.traverse(jsonParser.getCodec()), Wagon[].class));

        }

        Collection<Locomotive> locomotives = new ArrayList<>();
        final JsonNode veturis = node.get("veturis");
        if (veturis != null) {
            locomotives = Arrays.asList(jsonParser.getCodec().readValue(veturis.traverse(jsonParser.getCodec()), Locomotive[].class));
        }

        return new JourneyComposition(operator, trainNumber, departureDate, trainCategoryId, trainTypeId, totalLength, maximumSpeed,
                version, wagons, locomotives, journeyCompositionStartStation, journeyCompositionEndStation,id);
    }

    private static JourneyCompositionRow createJourneyCompositionRow(final JsonNode aikataulutapahtuma) {
        final LocalDateTime scheduledTime = LocalDateTime.parse(aikataulutapahtuma.get("tapahtumaAika").asText());
        final JsonNode liikennepaikka = aikataulutapahtuma.get("liikennepaikka");
        final String stationShortCode = liikennepaikka.get("lyhenne").asText();
        final int stationUICCode = liikennepaikka.get("uicKoodi").asInt();
        final String countryCode = liikennepaikka.get("maakoodi").asText();
        final TimeTableRow.TimeTableRowType type = "LAHTO".equals(aikataulutapahtuma.get("tyyppi")
                .asText()) ? TimeTableRow.TimeTableRowType.DEPARTURE : TimeTableRow.TimeTableRowType.ARRIVAL;
        return new JourneyCompositionRow(scheduledTime, stationShortCode, stationUICCode, countryCode, type);
    }
}
