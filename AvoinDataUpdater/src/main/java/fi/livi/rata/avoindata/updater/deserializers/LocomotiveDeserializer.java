package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;

@Component
public class LocomotiveDeserializer extends AEntityDeserializer<Locomotive> {
    @Value("#{'${updater.typesForVehicleNumberPublishinIsAllowed}'.split(',')}")
    private Set<String> typesForVehicleNumberPublishinIsAllowed;

    @Override
    public Locomotive deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException, com.fasterxml.jackson.core.JsonProcessingException {
        final JsonNode node = jp.getCodec().readTree(jp);

        Locomotive locomotive = new Locomotive();
        locomotive.location = node.get("sijainti").asInt();
        locomotive.powerTypeAbbreviation = node.get("vetovoimalajilyhenne").asText();
        locomotive.locomotiveType = node.get("tyyppi").asText();
        if (typesForVehicleNumberPublishinIsAllowed.contains(locomotive.locomotiveType)) {
            locomotive.vehicleNumber = getStringFromNode(node, "tunniste");
        }
        return locomotive;
    }
}
