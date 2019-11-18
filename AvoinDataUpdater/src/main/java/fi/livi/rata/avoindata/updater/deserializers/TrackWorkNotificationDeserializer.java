package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fi.livi.rata.avoindata.common.domain.trackwork.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class TrackWorkNotificationDeserializer extends AEntityDeserializer<TrackWorkNotification> {

    @Override
    public TrackWorkNotification deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        TrackWorkNotification trackWorkNotification = deSerializeTrackWorkNotifications(node);
        trackWorkNotification.trackWorkParts = deSerializeTrackWorkParts(node.get("tyonosat"), trackWorkNotification);
        return trackWorkNotification;
    }

    private TrackWorkNotification deSerializeTrackWorkNotifications(JsonNode node) {
        TrackWorkNotification trackWorkNotification = new TrackWorkNotification();
        trackWorkNotification.rumaId = node.get("id").asInt();
        trackWorkNotification.rumaVersion = node.get("version").asInt();
        trackWorkNotification.state = getState(getStringFromNode(node, "state"));
        trackWorkNotification.organization = getStringFromNode(node, "organization");
        trackWorkNotification.speedLimitPlan = getNullableBoolean(node, "nopeusrajoitusSuunnitelma");
        trackWorkNotification.created = getNodeAsDateTime(node.get("created"));
        trackWorkNotification.modified = getNodeAsDateTime(node.get("modified"));
        trackWorkNotification.speedLimitRemovalPlan = getNullableBoolean(node, "nopeusrajoituksenPoistosuunnitelma");
        trackWorkNotification.electricitySafetyPlan = getNullableBoolean(node, "jannitekatkoIlmoitus");
        trackWorkNotification.personInChargePlan = getNullableBoolean(node, "ratatyovastaavienVuorolista");
        trackWorkNotification.trafficSafetyPlan = getNullableBoolean(node, "liikenneturvallisuusSuunnitelma");
        return trackWorkNotification;
    }

    private Set<TrackWorkPart> deSerializeTrackWorkParts(JsonNode tyonosat, TrackWorkNotification trackWorkNotification) {
        Set<TrackWorkPart> trackWorkParts = new HashSet<>();
        for (final JsonNode trackWorkPartNode : tyonosat) {
            final TrackWorkPart trackWorkpart = new TrackWorkPart();
            trackWorkpart.partIndex = trackWorkPartNode.get("numero").asLong();
            trackWorkpart.startDay = getNodeAsLocalDate(trackWorkPartNode.get("aloituspaiva"));
            trackWorkpart.permissionMinimumDuration = Duration.parse(trackWorkPartNode.get("luvanMinimiPituus").asText());
            trackWorkpart.plannedWorkingGap = getLocalTimeFromNode(trackWorkPartNode, "suunniteltuTyorako");
            trackWorkpart.containsFireWork = getNullableBoolean(trackWorkPartNode, "sisaltaaTulityota");
            trackWorkpart.trackWorkNotification = trackWorkNotification;
            final List<String> advanceNotifications = new ArrayList<>();
            for (final JsonNode advanceNotificationNode : trackWorkPartNode.get("ennakkoilmoitukset")) {
                advanceNotifications.add(advanceNotificationNode.textValue());
            }
            trackWorkpart.advanceNotifications = advanceNotifications;
            trackWorkpart.locations = deSerializeRumaLocations(trackWorkPartNode.get("kohteet"), trackWorkpart);
            trackWorkParts.add(trackWorkpart);
        }
        return trackWorkParts;
    }

    private Set<RumaLocation> deSerializeRumaLocations(JsonNode locations, TrackWorkPart trackWorkpart) {
        Set<RumaLocation> rumaLocations = new HashSet<>();
        for (final JsonNode location : locations) {
            RumaLocation rumaLocation = new RumaLocation();
            rumaLocation.locationType = LocationType.fromKohdeType(location.get("type").asText());
            rumaLocation.operatingPointId = location.get("liikennepaikkaId").asText();
            rumaLocation.sectionBetweenOperatingPointsId = location.get("liikennepaikkavaliId").asText();
            rumaLocation.trackWorkPart = trackWorkpart;
            rumaLocations.add(rumaLocation);
        }
        return rumaLocations;
    }

    private TrackWorkNotificationState getState(String state) {
        switch (state) {
            case "ACTIVE":
                return TrackWorkNotificationState.ACTIVE;
            case "FINISHED":
                return TrackWorkNotificationState.FINISHED;
            case "PASSIVE":
            case "SENT":
                return TrackWorkNotificationState.PASSIVE;
        }
        throw new IllegalArgumentException(String.format("Could not produce State from %s", state));
    }
}
