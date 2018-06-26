package fi.livi.rata.avoindata.updater.deserializers;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.ThirdCategoryCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class DetailedCategoryCodeDeserializer extends AEntityDeserializer<DetailedCategoryCode> {
    @Override
    public DetailedCategoryCode deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        DetailedCategoryCode detailedCategoryCode = new DetailedCategoryCode();
        detailedCategoryCode.id = node.get("id").asLong();
        detailedCategoryCode.detailedCategoryCode = node.get("syykoodi").asText();
        detailedCategoryCode.detailedCategoryName = node.get("nimi").asText();
        detailedCategoryCode.validFrom = this.getNodeAsLocalDate(node.get("voimassaAlkupvm"));
        detailedCategoryCode.validTo = this.getNodeAsLocalDate(node.get("voimassaLoppupvm"));

        deserializeChildren(jsonParser, node.get("tarkentavaSyykoodiList"));

        final List<ThirdCategoryCode> thirdCategoryCodes = deserializeChildren(jsonParser, node.get("tarkentavaSyykoodiList"));

        for (final ThirdCategoryCode thirdCategoryCode : thirdCategoryCodes) {
            thirdCategoryCode.detailedCategoryCode = detailedCategoryCode;
        }
        detailedCategoryCode.thirdCategoryCodes.addAll(thirdCategoryCodes);

        return detailedCategoryCode;
    }


    private List<ThirdCategoryCode> deserializeChildren(final JsonParser jsonParser, final JsonNode nodes) throws IOException {
        return Arrays.asList(jsonParser.getCodec().readValue(nodes.traverse(jsonParser.getCodec()), ThirdCategoryCode[].class));
    }
}
