package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.vividsolutions.jts.geom.Geometry;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class TrackWorkNotificationDeserializer extends AEntityDeserializer<TrackWorkNotification> {

    @Override
    public TrackWorkNotification deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        final TrackWorkNotification trackWorkNotification = deserializeTrackWorkNotifications(node, jsonParser);
        trackWorkNotification.trackWorkParts = deserializeTrackWorkParts(node.get("tyonosat"), jsonParser);
         for (TrackWorkPart trackWorkPart : trackWorkNotification.trackWorkParts) {
             trackWorkPart.trackWorkNotification = trackWorkNotification;
         }
        return trackWorkNotification;
    }

    private Set<TrackWorkPart> deserializeTrackWorkParts(JsonNode workPartsNode, JsonParser jsonParser) throws IOException {
        return Set.of(jsonParser.getCodec().readValue(workPartsNode.traverse(jsonParser.getCodec()), TrackWorkPart[].class));
    }

    private TrackWorkNotification deserializeTrackWorkNotifications(JsonNode node, JsonParser jsonParser) throws IOException {
        final TrackWorkNotification trackWorkNotification = new TrackWorkNotification();
        trackWorkNotification.id = new TrackWorkNotification.TrackWorkNotificationId(node.get("id").asLong(), node.get("version").asLong());
        trackWorkNotification.state = getState(getStringFromNode(node, "state"));
        trackWorkNotification.organization = getStringFromNode(node, "organization");
        trackWorkNotification.speedLimitPlan = getNullableBoolean(node, "nopeusrajoitusSuunnitelma");
        trackWorkNotification.created = getNodeAsDateTime(node.get("created"));
        trackWorkNotification.modified = getNodeAsDateTime(node.get("modified"));
        trackWorkNotification.speedLimitRemovalPlan = getNullableBoolean(node, "nopeusrajoituksenPoistosuunnitelma");
        trackWorkNotification.electricitySafetyPlan = getNullableBoolean(node, "jannitekatkoIlmoitus");
        trackWorkNotification.personInChargePlan = getNullableBoolean(node, "ratatyovastaavienVuorolista");
        trackWorkNotification.trafficSafetyPlan = getNullableBoolean(node, "liikenneturvallisuusSuunnitelma");
        trackWorkNotification.locationMap = deserializeGeometry(node.get("karttapiste"), jsonParser);
        trackWorkNotification.locationSchema = deserializeGeometry(node.get("kaaviopiste"), jsonParser);
        return trackWorkNotification;
    }

    private Geometry deserializeGeometry(JsonNode node, JsonParser jsonParser) throws IOException {
        return jsonParser.getCodec().readValue(node.traverse(jsonParser.getCodec()), Geometry.class);
    }

    private TrackWorkNotificationState getState(String state) {
        switch (state) {
            case "ACTIVE":
                return TrackWorkNotificationState.ACTIVE;
            case "FINISHED":
                return TrackWorkNotificationState.FINISHED;
            case "PASSIVE":
                return TrackWorkNotificationState.PASSIVE;
            case "SENT":
                return TrackWorkNotificationState.SENT;
            case "DRAFT":
                return TrackWorkNotificationState.DRAFT;
            case "REMOVED":
                return TrackWorkNotificationState.REMOVED;
            case "REJECTED":
                return TrackWorkNotificationState.REJECTED;
        }
        throw new IllegalArgumentException(String.format("Could not produce State from %s", state));
    }
}
