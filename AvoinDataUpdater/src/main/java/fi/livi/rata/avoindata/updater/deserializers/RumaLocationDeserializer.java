package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import org.springframework.stereotype.Component;

import java.util.Set;

import static fi.livi.rata.avoindata.updater.service.ruma.RumaUtils.normalizeTrakediaInfraOid;

@Component
public class RumaLocationDeserializer extends AEntityDeserializer<RumaLocation> {

    @Override
    public RumaLocation deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) {
        final JsonNode locationNode = jsonParser.readValueAsTree();
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

    private Set<IdentifierRange> deserializeIdentifierRanges(final JsonNode irNode, final JsonParser jsonParser) {
        return Set.of(jsonParser.objectReadContext().readValue(irNode.traverse(jsonParser.objectReadContext()), IdentifierRange[].class));
    }
}
