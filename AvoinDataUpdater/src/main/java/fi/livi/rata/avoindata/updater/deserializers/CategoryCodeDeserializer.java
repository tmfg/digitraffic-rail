package fi.livi.rata.avoindata.updater.deserializers;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class CategoryCodeDeserializer extends AEntityDeserializer<CategoryCode> {
    @Override
    public CategoryCode deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        CategoryCode categoryCode = new CategoryCode();
        categoryCode.id = node.get("id").asLong();
        categoryCode.categoryName = node.get("nimi").asText();
        categoryCode.categoryCode = node.get("tunnus").asText();
        categoryCode.validFrom = this.getNodeAsLocalDate(node.get("voimassaAlkupvm"));
        categoryCode.validTo = this.getNodeAsLocalDate(node.get("voimassaLoppupvm"));

        final List<DetailedCategoryCode> detailedCategoryCodes = deserializeChildren(jsonParser, node.get("syykoodis"));

        for (final DetailedCategoryCode detailedCategoryCode : detailedCategoryCodes) {
            detailedCategoryCode.categoryCode = categoryCode;
        }
        categoryCode.detailedCategoryCodes.addAll(detailedCategoryCodes);

        return categoryCode;
    }

    private List<DetailedCategoryCode> deserializeChildren(final JsonParser jsonParser, final JsonNode nodes) throws IOException {
        return Arrays.asList(jsonParser.getCodec().readValue(nodes.traverse(jsonParser.getCodec()), DetailedCategoryCode[].class));
    }
}
