package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

import static fi.livi.rata.avoindata.updater.service.ruma.RumaUtils.normalizeTrakediaInfraOid;

@Component
public class RumaLocationDeserializer extends AEntityDeserializer<RumaLocation> {

    @Override
    public RumaLocation deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode locationNode = jsonParser.getCodec().readTree(jsonParser);
        final RumaLocation rumaLocation = new RumaLocation();
        rumaLocation.locationType = LocationType.fromKohdeType(locationNode.get("type").asText());
        final JsonNode operatingPointNode = locationNode.get("liikennepaikkaId");
        rumaLocation.operatingPointId = operatingPointNode.isNull() ? null :normalizeTrakediaInfraOid(operatingPointNode.asText());
        final JsonNode sectionBetweenOperatingPointsNode = locationNode.get("liikennepaikkavaliId");
        rumaLocation.sectionBetweenOperatingPointsId = sectionBetweenOperatingPointsNode.isNull() ? null : normalizeTrakediaInfraOid(sectionBetweenOperatingPointsNode.asText());
        rumaLocation.locationMap = deserializeGeometry(locationNode.get("sijainti"), jsonParser);
        rumaLocation.locationSchema = deserializeGeometry(locationNode.get("kaaviosijainti"), jsonParser);
        rumaLocation.identifierRanges = deserializeIdentifierRanges(locationNode.get("tunnusvalit"), jsonParser);
        for (final IdentifierRange ir : rumaLocation.identifierRanges) {
            ir.location = rumaLocation;
        }
        return rumaLocation;
    }

    private Set<IdentifierRange> deserializeIdentifierRanges(final JsonNode irNode, final JsonParser jsonParser) throws IOException {
        return Set.of(jsonParser.getCodec().readValue(irNode.traverse(jsonParser.getCodec()), IdentifierRange[].class));
    }
}
