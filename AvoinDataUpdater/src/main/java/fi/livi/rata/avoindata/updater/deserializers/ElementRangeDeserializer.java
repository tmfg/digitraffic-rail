package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.trackwork.ElementRange;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static fi.livi.rata.avoindata.updater.service.ruma.RumaUtils.normalizeTrakediaInfraOid;

@Component
public class ElementRangeDeserializer extends AEntityDeserializer<ElementRange> {

    @Override
    public ElementRange deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) {
        final JsonNode elementRangeNode = jsonParser.readValueAsTree();
        final ElementRange elementRange = new ElementRange();
        elementRange.elementId1 = normalizeTrakediaInfraOid(elementRangeNode.get("elementtiId1").asText());
        elementRange.elementId2 = normalizeTrakediaInfraOid(elementRangeNode.get("elementtiId2").asText());
        final List<String> trackIds = new ArrayList<>();
        for (final JsonNode trackIdNode : elementRangeNode.get("raideIds")) {
            trackIds.add(normalizeTrakediaInfraOid(trackIdNode.textValue()));
        }
        elementRange.trackIds = trackIds;
        final List<String> specifiers = new ArrayList<>();
        for (final JsonNode specifierNode : elementRangeNode.get("tarkenteet")) {
            specifiers.add(normalizeTrakediaInfraOid(specifierNode.textValue()));
        }
        elementRange.specifiers = specifiers;
        elementRange.trackKilometerRange = getNullableRumaRatakmvali(elementRangeNode, "ratakmvali");
        return elementRange;
    }

}
