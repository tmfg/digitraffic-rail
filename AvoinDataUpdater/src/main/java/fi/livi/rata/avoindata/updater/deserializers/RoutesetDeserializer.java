package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class RoutesetDeserializer extends AEntityDeserializer<Routeset> {
    @Override
    public Routeset deserialize(final JsonParser jsonParser,
                                final DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        Routeset routeset = new Routeset();

        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        routeset.id = node.get("id").asLong();
        final String trainNumber = getNullableString(node, "trainNumber");
        final LocalDate departureDate = this.getNodeAsLocalDate(node.get("departureDate"));
        routeset.trainId = new StringTrainId(trainNumber, departureDate);
        routeset.version = node.get("version").asLong();
        routeset.messageTime = this.getNodeAsDateTime(node.get("messageTime"));
        routeset.clientSystem = getNullableString(node, "clientSystem");
        routeset.routeType = getNullableString(node, "routeType");

        final JsonNode routesectionNodes = node.get("routesections");
        if (routesectionNodes != null) {
            final Set<Routesection> routesections = deserializeRoutesections(jsonParser, routesectionNodes);
            routeset.routesections.addAll(routesections);
            for (final Routesection routesection : routesections) {
                routesection.routeset = routeset;
            }
        }

        return routeset;
    }

    private static Set<Routesection> deserializeRoutesections(final JsonParser jsonParser, final JsonNode node) throws IOException {
        return new HashSet<>(Arrays.asList(jsonParser.getCodec().readValue(node.traverse(jsonParser.getCodec()), Routesection[].class)));
    }
}
