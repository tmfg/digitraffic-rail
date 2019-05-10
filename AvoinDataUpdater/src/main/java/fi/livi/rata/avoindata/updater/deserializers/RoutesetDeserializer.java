package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Component
public class RoutesetDeserializer extends AEntityDeserializer<Routeset> {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RoutesetRepository routesetRepository;

    @Override
    public Routeset deserialize(final JsonParser jsonParser,
                                final DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        Routeset routeset = new Routeset();

        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        routeset.id = node.get("id").asLong();
        final String trainNumber = getNullableString(node, "trainNumber");
        final LocalDate departureDate = this.getNodeAsLocalDate(node.get("departureDate"));
        routeset.trainId = new StringTrainId(trainNumber, departureDate);
        JsonNode versionNode = node.get("version");
        if (versionNode != null) {
            routeset.version = versionNode.asLong();
        } else {
            routeset.version = routesetRepository.getMaxVersion() + 1;
            log.info("Made up version for {}/{}/{}, version:{} ", trainNumber, departureDate, routeset.id, routeset.version);
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

    private static List<Routesection> deserializeRoutesections(final JsonParser jsonParser, final JsonNode node) throws IOException {
        return Lists.newArrayList(jsonParser.getCodec().readValue(node.traverse(jsonParser.getCodec()), Routesection[].class));
    }
}
