package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RoutesectionDeserializer extends AEntityDeserializer<Routesection> {
    @Override
    public Routesection deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        Routesection routesection = new Routesection();

        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        routesection.id = node.get("id").asLong();
        routesection.sectionId = getNullableString(node,"sectionId");
        routesection.stationCode = getNullableString(node,"stationCode");
        routesection.sectionOrder = node.get("sectionOrder").asInt();
        routesection.commercialTrackId = getNullableString(node,"commercialTrackId");

        return routesection;
    }
}
