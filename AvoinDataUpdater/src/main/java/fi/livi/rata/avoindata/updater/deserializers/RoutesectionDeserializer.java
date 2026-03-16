package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import org.springframework.stereotype.Component;


@Component
public class RoutesectionDeserializer extends AEntityDeserializer<Routesection> {
    @Override
    public Routesection deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) {
        final Routesection routesection = new Routesection();

        final JsonNode node = jsonParser.readValueAsTree();

        routesection.id = node.get("id").asLong();
        routesection.sectionId = getNullableString(node,"sectionId");
        routesection.stationCode = getNullableString(node,"stationCode");
        routesection.sectionOrder = node.get("sectionOrder").asInt();
        routesection.commercialTrackId = getNullableString(node,"commercialTrackId");

        return routesection;
    }
}
