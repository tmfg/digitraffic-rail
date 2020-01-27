package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class TrackWorkPartDeserializer extends AEntityDeserializer<TrackWorkPart> {

    @Override
    public TrackWorkPart deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode trackWorkPartNode = jsonParser.getCodec().readTree(jsonParser);
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
        for (RumaLocation location : trackWorkpart.locations) {
            location.trackWorkPart = trackWorkpart;
        }
        return trackWorkpart;
    }

    private Set<RumaLocation> deserializeRumaLocations(JsonNode rumaLocationsNode, JsonParser jsonParser) throws IOException {
        return Set.of(jsonParser.getCodec().readValue(rumaLocationsNode.traverse(jsonParser.getCodec()), RumaLocation[].class));
    }

}
