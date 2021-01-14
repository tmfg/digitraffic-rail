package fi.livi.rata.avoindata.updater.deserializers;


import java.io.IOException;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
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

        final JsonNode syykoodi = node.get("syykoodi");
        final JsonNode syyluokka = syykoodi.get("syyluokka");
        final JsonNode tarkentavaSyykoodi = node.get("tarkentavaSyykoodi");

        if (node == null || node.get("version") == null) {
            cause.version = -1;
        } else {
            cause.version = node.get("version").asLong();
        }

        if (!isNodeNull(syyluokka)) {
            CategoryCode categoryCode = entityManager.find(CategoryCode.class, syyluokka.get("id").asLong());
            cause.categoryCode = categoryCode;

            if (!isNodeNull(syykoodi)) {
                DetailedCategoryCode detailedCategoryCode = entityManager.find(DetailedCategoryCode.class, syykoodi.get("id").asLong());
                cause.detailedCategoryCode = detailedCategoryCode;
            }

            if (!isNodeNull(tarkentavaSyykoodi)) {
                ThirdCategoryCode thirdCategoryCode = entityManager.find(ThirdCategoryCode.class, tarkentavaSyykoodi.get("id").asLong());
                cause.thirdCategoryCode = thirdCategoryCode;
            }
        }

        return cause;
    }

}
