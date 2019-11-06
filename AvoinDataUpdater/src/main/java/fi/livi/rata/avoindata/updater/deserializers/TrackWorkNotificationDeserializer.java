package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;

@Component
public class TrackWorkNotificationDeserializer extends AEntityDeserializer<TrackWorkNotification> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public TrackWorkNotification deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {

        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        TrackWorkNotification trackWorkNotification = new TrackWorkNotification();
        trackWorkNotification.rumaId = node.get("id").asInt();
        trackWorkNotification.rumaVersion = node.get("version").asInt();
        trackWorkNotification.state = getState(getStringFromNode(node, "state"));
        trackWorkNotification.description = getStringFromNode(node, "kuvausJaKalusto");
        trackWorkNotification.organization = getStringFromNode(node, "organization");
        trackWorkNotification.speedLimitPlan = getNullableBoolean(node, "nopeusrajoitusSuunnitelma");
        trackWorkNotification.created = getNodeAsDateTime(node.get("created"));
        trackWorkNotification.modified = getNodeAsDateTime(node.get("modified"));
        trackWorkNotification.speedLimitRemovalPlan = getNullableBoolean(node, "nopeusrajoituksenPoistosuunnitelma");
        trackWorkNotification.electricitySafetyPlan = getNullableBoolean(node, "jannitekatkoIlmoitus");
        trackWorkNotification.personInChargePlan = getNullableBoolean(node, "ratatyovastaavienVuorolista");
        trackWorkNotification.trafficSafetyPlan = getNullableBoolean(node, "liikenneturvallisuusSuunnitelma");
        trackWorkNotification.parts = objectMapper.writeValueAsString(node.get("tyonosat"));

        return trackWorkNotification;
    }

    private TrackWorkNotificationState getState(String state) {
        if (state.equals("ACTIVE")) {
            return TrackWorkNotificationState.ACTIVE;
        } else if (state.equals("FINISHED")) {
            return TrackWorkNotificationState.FINISHED;
        } else if (state.equals("PASSIVE")) {
            return TrackWorkNotificationState.PASSIVE;
        } else if (state.equals("SENT")) {
            return TrackWorkNotificationState.PASSIVE;
        }

        throw new IllegalArgumentException(String.format("Could not produce State from %s", state));
    }
}
