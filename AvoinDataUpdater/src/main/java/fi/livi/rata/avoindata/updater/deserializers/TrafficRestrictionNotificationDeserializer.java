package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class TrafficRestrictionNotificationDeserializer extends AEntityDeserializer<TrafficRestrictionNotification> {

    @Override
    public TrafficRestrictionNotification deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        return deserializeTrafficRestrictionNotifications(node, jsonParser);
    }

    private TrafficRestrictionNotification deserializeTrafficRestrictionNotifications(JsonNode node, JsonParser jsonParser) throws IOException {
        final TrafficRestrictionNotification trafficRestrictionNotification = new TrafficRestrictionNotification();
        trafficRestrictionNotification.id = new TrafficRestrictionNotification.TrafficRestrictionNotificationId(node.get("id").asLong(), node.get("version").asLong());
        trafficRestrictionNotification.state = getState(getStringFromNode(node, "state"));
        trafficRestrictionNotification.organization = getStringFromNode(node, "organization");
        trafficRestrictionNotification.created = getNodeAsDateTime(node.get("created"));
        trafficRestrictionNotification.modified = getNodeAsDateTime(node.get("modified"));
        trafficRestrictionNotification.twnId = getNullableString(node, "ratatyoilmoitusId");
        trafficRestrictionNotification.finished = getNodeAsDateTime(node.get("finished"));
        trafficRestrictionNotification.startDate = getNodeAsDateTime(node.get("voimassaAlku"));
        trafficRestrictionNotification.endDate = getNodeAsDateTime(node.get("voimassaLoppu"));
        trafficRestrictionNotification.locationMap = deserializeGeometry(node.get("karttapiste"), jsonParser);
        trafficRestrictionNotification.locationSchema = deserializeGeometry(node.get("kaaviopiste"), jsonParser);

        JsonNode rajoiteNode = node.get("rajoite");
        trafficRestrictionNotification.limitation = getType(rajoiteNode.get("tyyppi").textValue());
        trafficRestrictionNotification.limitationDescription = getNullableString(rajoiteNode, "rajoiteKuvaus");
        trafficRestrictionNotification.axleWeightMax = getNullableDouble(rajoiteNode, "akselipainoMaxFloat");

        trafficRestrictionNotification.locations = deserializeRumaLocations(node.get("kohteet"), jsonParser);
        for (RumaLocation location : trafficRestrictionNotification.locations) {
            location.trafficRestrictionNotification = trafficRestrictionNotification;
        }

        return trafficRestrictionNotification;
    }

    private Set<RumaLocation> deserializeRumaLocations(JsonNode rumaLocationsNode, JsonParser jsonParser) throws IOException {
        return Set.of(jsonParser.getCodec().readValue(rumaLocationsNode.traverse(jsonParser.getCodec()), RumaLocation[].class));
    }

    private TrafficRestrictionType getType(String type) {
        switch (type) {
            case "SULJETTU_LIIKENNOINNILTA":
                return TrafficRestrictionType.CLOSED_FROM_TRAFFIC;
            case "SULJETTU_SAHKOVETOKALUSTOLTA":
                return TrafficRestrictionType.CLOSED_FROM_ELECTRIC_ROLLING_STOCK;
            case "TILAPAINEN_NOPEUSRAJOITUS":
                return TrafficRestrictionType.TEMPORARY_SPEED_LIMIT;
            case "AKSELIPAINO_MAX":
                return TrafficRestrictionType.AXLE_WEIGHT_MAX;
            case "JKV_RAKENNUSALUE":
                return TrafficRestrictionType.ATP_CONSTRUCTION_ZONE;
            case "VAIHTEEN_LUKITUS":
                return TrafficRestrictionType.SWITCH_LOCKED;
            case "TULITYON_VAARA_ALUE":
                return TrafficRestrictionType.FIREWORK_DANGER_ZONE;
            case "MUU":
                return TrafficRestrictionType.OTHER;

        }
        throw new IllegalArgumentException(String.format("Could not produce type from %s", type));
    }

    private TrafficRestrictionNotificationState getState(String state) {
        switch (state) {
            case "FINISHED":
                return TrafficRestrictionNotificationState.FINISHED;
            case "SENT":
                return TrafficRestrictionNotificationState.SENT;
            case "DRAFT":
                return TrafficRestrictionNotificationState.DRAFT;
            case "REMOVED":
                return TrafficRestrictionNotificationState.REMOVED;
        }
        throw new IllegalArgumentException(String.format("Could not produce State from %s", state));
    }
}
