package fi.livi.rata.avoindata.updater.deserializers;


import java.io.IOException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.ThirdCategoryCode;

@Component
public class CauseDeserializer extends AEntityDeserializer<Cause> {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Cause deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        Cause cause = new Cause();

        final JsonNode syykoodi = node.get("syykoodiOid");
        final JsonNode tarkentavaSyykoodi = node.get("tarkentavaSyykoodiOid");

        if (node == null || node.get("version") == null) {
            cause.version = -1;
        } else {
            cause.version = node.get("version").asLong();
        }


        if (!isNodeNull(syykoodi)) {
            DetailedCategoryCode detailedCategoryCode = entityManager.find(DetailedCategoryCode.class, syykoodi.asText());
            if (detailedCategoryCode != null) {
                cause.detailedCategoryCode = detailedCategoryCode;
                cause.categoryCode = detailedCategoryCode.categoryCode;
            }
        }

        if (!isNodeNull(tarkentavaSyykoodi)) {
            ThirdCategoryCode thirdCategoryCode = entityManager.find(ThirdCategoryCode.class, tarkentavaSyykoodi.asText());
            if (thirdCategoryCode != null) {
                cause.thirdCategoryCode = thirdCategoryCode;
            }
        }

        if (cause.detailedCategoryCode != null) {
            return cause;
        } else {
            return null;
        }
    }
}
