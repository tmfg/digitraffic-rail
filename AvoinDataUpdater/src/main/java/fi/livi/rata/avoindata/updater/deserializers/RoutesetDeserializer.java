package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class RoutesetDeserializer extends AEntityDeserializer<Routeset> {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RoutesetRepository routesetRepository;

    @Override
    public Routeset deserialize(final JsonParser jsonParser,
                                final DeserializationContext deserializationContext) {
        final Routeset routeset = new Routeset();

        final JsonNode node = jsonParser.readValueAsTree();

        routeset.id = node.get("id").asLong();
        final String trainNumber = getNullableString(node, "trainNumber");
        final LocalDate departureDate = this.getNodeAsLocalDate(node.get("departureDate"));
        routeset.trainId = new StringTrainId(trainNumber, departureDate);
        final JsonNode versionNode = node.get("version");
        if (versionNode != null) {
            routeset.version = versionNode.asLong();
        } else {
            routeset.version = routesetRepository.getMaxVersion() + 1;
            log.debug("Made up version for {}/{}/{}, version:{} ", trainNumber, departureDate, routeset.id, routeset.version);
        }
        routeset.messageId = node.get("messageId").asText();
        routeset.messageTime = this.getNodeAsDateTime(node.get("messageTime"));
        routeset.clientSystem = getNullableString(node, "clientSystem");
        routeset.routeType = getNullableString(node, "routeType");

        final JsonNode routesectionNodes = node.get("routesections");
        if (routesectionNodes != null) {
            final List<Routesection> routesections = deserializeRoutesections(jsonParser, routesectionNodes);

            routeset.routesections.addAll(routesections);
            for (final Routesection routesection : routesections) {
                routesection.routeset = routeset;
            }
        }

        return routeset;
    }

    private static List<Routesection> deserializeRoutesections(final JsonParser jsonParser, final JsonNode node) {
        return Lists.newArrayList(jsonParser.objectReadContext().readValue(node.traverse(jsonParser.objectReadContext()), Routesection[].class));
    }
}
