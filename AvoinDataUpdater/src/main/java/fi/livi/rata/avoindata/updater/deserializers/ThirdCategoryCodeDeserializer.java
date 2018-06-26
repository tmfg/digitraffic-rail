package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.cause.ThirdCategoryCode;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ThirdCategoryCodeDeserializer extends AEntityDeserializer<ThirdCategoryCode> {
    @Override
    public ThirdCategoryCode deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        final ThirdCategoryCode thirdCategoryCode = new ThirdCategoryCode();

        thirdCategoryCode.id = node.get("id").asLong();
        thirdCategoryCode.thirdCategoryCode = node.get("tark_syykoodi").asText();
        thirdCategoryCode.thirdCategoryName = node.get("nimi").asText();

        thirdCategoryCode.validFrom = this.getNodeAsLocalDate(node.get("voimassaAlkupvm"));
        thirdCategoryCode.validTo = this.getNodeAsLocalDate(node.get("voimassaLoppupvm"));

        thirdCategoryCode.description = getNullableString(node, "kuvaus");

        return thirdCategoryCode;
    }

}
