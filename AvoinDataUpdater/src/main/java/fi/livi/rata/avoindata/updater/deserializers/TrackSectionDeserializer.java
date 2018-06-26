package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackRange;
import fi.livi.rata.avoindata.common.domain.tracksection.TrackSection;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

@Component
public class TrackSectionDeserializer extends AEntityDeserializer<TrackSection> {
    @Override
    public TrackSection deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        TrackSection trackSection = new TrackSection();
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        trackSection.id = node.get("id").asLong();
        trackSection.trackSectionCode = node.get("tunniste").asText();
        trackSection.station = getStringFromNode(node.get("liikennepaikka"), "lyhenne");

        final List<TrackRange> trackRanges = getObjectsFromNode(jsonParser, node, TrackRange[].class, "raideosuudenSijaintis");
        for (final TrackRange trackRange : trackRanges) {
            trackRange.trackSection = trackSection;
        }

        trackSection.ranges = new HashSet<>(trackRanges);

        return trackSection;
    }
}
