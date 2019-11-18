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
import java.util.*;

@Component
public class TrackWorkNotificationDeserializer extends AEntityDeserializer<TrackWorkNotification> {

    @Override
    public TrackWorkNotification deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        final TrackWorkNotification trackWorkNotification = deserializeTrackWorkNotifications(node);
        trackWorkNotification.trackWorkParts = deserializeTrackWorkParts(node.get("tyonosat"), trackWorkNotification);
        return trackWorkNotification;
    }

    private TrackWorkNotification deserializeTrackWorkNotifications(JsonNode node) {
        final TrackWorkNotification trackWorkNotification = new TrackWorkNotification();
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

    private Set<TrackWorkPart> deserializeTrackWorkParts(JsonNode tyonosat, TrackWorkNotification trackWorkNotification) {
        final Set<TrackWorkPart> trackWorkParts = new HashSet<>();
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
            trackWorkpart.locations = deserializeRumaLocations(trackWorkPartNode.get("kohteet"), trackWorkpart);
            trackWorkParts.add(trackWorkpart);
        }
        return trackWorkParts;
    }

    private Set<RumaLocation> deserializeRumaLocations(JsonNode locations, TrackWorkPart trackWorkpart) {
        final Set<RumaLocation> rumaLocations = new HashSet<>();
        for (final JsonNode locationNode : locations) {
            RumaLocation rumaLocation = new RumaLocation();
            rumaLocation.locationType = LocationType.fromKohdeType(locationNode.get("type").asText());
            rumaLocation.operatingPointId = locationNode.get("liikennepaikkaId").asText();
            rumaLocation.sectionBetweenOperatingPointsId = locationNode.get("liikennepaikkavaliId").asText();
            rumaLocation.identifierRanges = deserializeIdentifierRanges(locationNode.get("tunnusvalit"), rumaLocation);
            rumaLocation.trackWorkPart = trackWorkpart;
            rumaLocations.add(rumaLocation);
        }
        return rumaLocations;
    }

    private Set<IdentifierRange> deserializeIdentifierRanges(JsonNode identifierRangeNodes, RumaLocation rumaLocation) {
        final Set<IdentifierRange> identifierRanges = new HashSet<>();
        for (final JsonNode identifierRangeNode : identifierRangeNodes) {
            final IdentifierRange identifierRange = new IdentifierRange();
            identifierRange.elementId = identifierRangeNode.get("elementtiId").asText();
            identifierRange.elementPairId1 = identifierRangeNode.get("elementtipariId1").asText();
            identifierRange.elementPairId2 = identifierRangeNode.get("elementtipariId2").asText();
            identifierRange.speedLimit = deserializeSpeedLimit(identifierRangeNode.get("nopeusrajoitus"));
            identifierRange.elementRanges = deserializeElementRanges(identifierRangeNode.get("elementtivalit"), identifierRange);
            identifierRange.location = rumaLocation;
            identifierRanges.add(identifierRange);
        }
        return identifierRanges;
    }

    private Set<ElementRange> deserializeElementRanges(JsonNode elementRangeNodes, IdentifierRange identifierRange) {
        final Set<ElementRange> elementRanges = new HashSet<>();
        for (final JsonNode elementRangeNode : elementRangeNodes) {
            final ElementRange elementRange = new ElementRange();
            elementRange.elementId1 = elementRangeNode.get("elementtiId1").asText();
            elementRange.elementId2 = elementRangeNode.get("elementtiId2").asText();
            final List<String> trackIds = new ArrayList<>();
            for (final JsonNode trackIdNode : elementRangeNode.get("raideIds")) {
                trackIds.add(trackIdNode.textValue());
            }
            elementRange.trackIds = trackIds;
            final List<String> specifiers = new ArrayList<>();
            for (final JsonNode specifierNode : elementRangeNode.get("tarkenteet")) {
                specifiers.add(specifierNode.textValue());
            }
            elementRange.specifiers = specifiers;
            elementRange.identifierRange = identifierRange;
            elementRanges.add(elementRange);
        }
        return elementRanges;
    }

    private SpeedLimit deserializeSpeedLimit(JsonNode speedLimitNode) {
        final JsonNode speedNode = speedLimitNode.get("nopeus");
        final JsonNode signsNode = speedLimitNode.get("merkit");
        final JsonNode balisesNode = speedLimitNode.get("baliisit");
         return speedNode != null && signsNode != null && balisesNode != null ?
                new SpeedLimit(speedNode.asInt(), signsNode.asBoolean(), balisesNode.asBoolean()) : null;
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
