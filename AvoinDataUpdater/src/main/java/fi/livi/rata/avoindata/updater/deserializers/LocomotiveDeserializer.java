package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;

import fi.finrail.koju.model.OsavalinVeturiDto;
import fi.finrail.koju.model.TractionType;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;

@Component
public class LocomotiveDeserializer extends AEntityDeserializer<Locomotive> {
    private static final Logger log = LoggerFactory.getLogger(LocomotiveDeserializer.class);

    private final String[] typesForWhichVehicleNumberPublishingIsAllowed;
    private final WagonDeserializer wagonDeserializer;

    public LocomotiveDeserializer(
            @Value("${updater.typesForVehicleNumberPublishinIsAllowed}")
            final String typesForWhichVehicleNumberPublishingIsAllowed,
            final WagonDeserializer wagonDeserializer) {
        this.typesForWhichVehicleNumberPublishingIsAllowed = typesForWhichVehicleNumberPublishingIsAllowed.split(",");
        this.wagonDeserializer = wagonDeserializer;
        log.info("method=LocomotiveDeserializer typesForWhichVehicleNumberPublishingIsAllowed: {}", typesForWhichVehicleNumberPublishingIsAllowed);
    }

    @Override
    public Locomotive deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException {
        final JsonNode node = jp.getCodec().readTree(jp);

        final Locomotive locomotive = new Locomotive();
        locomotive.location = node.get("sijainti").asInt();
        locomotive.powerTypeAbbreviation = node.get("vetovoimalajilyhenne").asText();
        locomotive.locomotiveType = node.get("tyyppi").asText();
        if (StringUtils.containsAnyIgnoreCase(locomotive.locomotiveType, typesForWhichVehicleNumberPublishingIsAllowed)) {
            locomotive.vehicleNumber = getNullableString(node, "tunniste");
        }
        return locomotive;
    }

    public Locomotive transformToLocomotive(final OsavalinVeturiDto veturiDto, final Integer trainNumber) {
        return new Locomotive(
                veturiDto.getJarjestysnumero(), // final int location // node.get("sijainti").asInt();
                veturiDto.getSarjatunnus(), // final String locomotiveType, sarjatunnus is concluded from EVN // node.get("tyyppi").asText();
                getpowerTypeAbbreviation(veturiDto.getTractionType()), // powerTypeAbbreviation // node.get("vetovoimalajilyhenne").asText();
                wagonDeserializer.getVehicleNumber(veturiDto.getEurooppatunnus(), veturiDto.getTunniste(), veturiDto.getSarjatunnus(), trainNumber) // final String vehicleNumber // getNullableString(node, "tunniste")
        );
    }

    // Sähkö, Diesel, Höyry
    private static final List<String> ALLOWED_POWER_TYPE_ABBREVIATIONS = Arrays.asList("S", "D", "H");

    private String getpowerTypeAbbreviation(final TractionType tractionType) {
        if (tractionType == null || StringUtils.isBlank(tractionType.getVoimanlahde())) {
            return null;
        }
        // Compare first character to predefined values for
        final String abbr = tractionType.getVoimanlahde().substring(0, 1).toUpperCase();
        if (ALLOWED_POWER_TYPE_ABBREVIATIONS.contains(abbr)) {
            return abbr;
        }
        return null;
    }

}
