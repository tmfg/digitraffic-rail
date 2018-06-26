package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LocomotiveDeserializer extends JsonDeserializer<Locomotive> {
    @Override
    public Locomotive deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException, com.fasterxml.jackson.core.JsonProcessingException {
        final JsonNode node = jp.getCodec().readTree(jp);

        Locomotive locomotive = new Locomotive();
        locomotive.location = node.get("sijainti").asInt();
        locomotive.powerTypeAbbreviation = node.get("vetovoimalajilyhenne").asText();
        locomotive.locomotiveType = node.get("tyyppi").asText();
        return locomotive;
    }
}
