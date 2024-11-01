package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackLocation;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackRange;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TrackRangeDeserializer extends AEntityDeserializer<TrackRange> {
    @Override
    public TrackRange deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final TrackRange trackRange = new TrackRange();
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

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
