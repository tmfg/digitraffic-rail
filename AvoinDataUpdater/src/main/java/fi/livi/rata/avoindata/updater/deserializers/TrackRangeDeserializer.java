package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackLocation;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackRange;
import org.springframework.stereotype.Component;


@Component
public class TrackRangeDeserializer extends AEntityDeserializer<TrackRange> {
    @Override
    public TrackRange deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) {
        final TrackRange trackRange = new TrackRange();
        final JsonNode node = jsonParser.readValueAsTree();

        trackRange.id = node.get("id").asLong();
        trackRange.startLocation = createTracklocation(node, "alku");
        trackRange.endLocation = createTracklocation(node, "loppu");

        return trackRange;
    }

    private TrackLocation createTracklocation(final JsonNode node, final String prefix) {
        final TrackLocation trackLocation = new TrackLocation();

        trackLocation.track = node.get(prefix + "Ratanumero").asText();
        final String[] trackLocationStrings = node.get(prefix + "KM").asText().split("\\+");
        trackLocation.kilometres = Integer.valueOf(trackLocationStrings[0]);
        trackLocation.metres = Integer.valueOf(trackLocationStrings[1]);
        return trackLocation;
    }
}
