package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static fi.livi.rata.avoindata.updater.service.ruma.RumaUtils.normalizeJetiOid;

@Component
public class TrackWorkPartDeserializer extends AEntityDeserializer<TrackWorkPart> {

    @Override
    public TrackWorkPart deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) {
        final JsonNode trackWorkPartNode = jsonParser.readValueAsTree();
        final TrackWorkPart trackWorkpart = new TrackWorkPart();
        trackWorkpart.partIndex = trackWorkPartNode.get("numero").asLong();
        trackWorkpart.startDay = getNodeAsLocalDate(trackWorkPartNode.get("aloituspaiva"));
        trackWorkpart.permissionMinimumDuration = Duration.parse(trackWorkPartNode.get("luvanMinimiPituus").asText());
        trackWorkpart.plannedWorkingGap = getLocalTimeFromNode(trackWorkPartNode, "suunniteltuTyorako");
        trackWorkpart.containsFireWork = getNullableBoolean(trackWorkPartNode, "sisaltaaTulityota");
        final List<String> advanceNotifications = new ArrayList<>();
        for (final JsonNode advanceNotificationNode : trackWorkPartNode.get("ennakkoilmoitukset")) {
            advanceNotifications.add(normalizeJetiOid(advanceNotificationNode.textValue()));
        }
        trackWorkpart.advanceNotifications = advanceNotifications;
        trackWorkpart.locations = deserializeRumaLocations(trackWorkPartNode.get("kohteet"), jsonParser);
        return trackWorkpart;
    }

    private Set<RumaLocation> deserializeRumaLocations(JsonNode rumaLocationsNode, JsonParser jsonParser) {
        return Set.of(jsonParser.objectReadContext().readValue(rumaLocationsNode.traverse(jsonParser.objectReadContext()), RumaLocation[].class));
    }

}
