package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.trackwork.ElementRange;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.SpeedLimit;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

import static fi.livi.rata.avoindata.updater.service.ruma.RumaUtils.normalizeTrakediaInfraOid;

@Component
public class IdentifierRangeDeserializer extends AEntityDeserializer<IdentifierRange> {

    @Override
    public IdentifierRange deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode identifierRangeNode = jsonParser.getCodec().readTree(jsonParser);
        final IdentifierRange identifierRange = new IdentifierRange();
        JsonNode elementNode = identifierRangeNode.get("elementtiId");
        identifierRange.elementId = elementNode.isNull() ? null : normalizeTrakediaInfraOid(elementNode.asText());
        JsonNode elementPairNode1 = identifierRangeNode.get("elementtipariId1");
        identifierRange.elementPairId1 = elementPairNode1.isNull() ? null : normalizeTrakediaInfraOid(elementPairNode1.asText());
        JsonNode elementPairNode2 = identifierRangeNode.get("elementtipariId2");
        identifierRange.elementPairId2 = elementPairNode2.isNull() ? null : normalizeTrakediaInfraOid(elementPairNode2.asText());
        identifierRange.locationMap = deserializeGeometry(identifierRangeNode.get("sijainti"), jsonParser);
        identifierRange.locationSchema = deserializeGeometry(identifierRangeNode.get("kaaviosijainti"), jsonParser);
        identifierRange.speedLimit = deserializeSpeedLimit(identifierRangeNode.get("nopeusrajoitus"));
        identifierRange.elementRanges = deserializeElementRanges(identifierRangeNode.get("elementtivalit"), jsonParser);
        for (ElementRange elementRange : identifierRange.elementRanges) {
            elementRange.identifierRange = identifierRange;
        }
        return identifierRange;
    }

    private Set<ElementRange> deserializeElementRanges(JsonNode erNode, JsonParser jsonParser) throws IOException {
        return Set.of(jsonParser.getCodec().readValue(erNode.traverse(jsonParser.getCodec()), ElementRange[].class));
    }

    private SpeedLimit deserializeSpeedLimit(JsonNode speedLimitNode) {
        final JsonNode speedNode = speedLimitNode.get("nopeus");
        final JsonNode signsNode = speedLimitNode.get("merkit");
        final JsonNode balisesNode = speedLimitNode.get("baliisit");
         return speedNode != null && signsNode != null && balisesNode != null ?
                new SpeedLimit(speedNode.asInt(), signsNode.asBoolean(), balisesNode.asBoolean()) : null;
    }

}
