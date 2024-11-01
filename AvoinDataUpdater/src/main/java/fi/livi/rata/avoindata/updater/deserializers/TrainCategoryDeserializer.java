package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TrainCategoryDeserializer extends JsonDeserializer<TrainCategory> {
    @Override
    public TrainCategory deserialize(final JsonParser jp,
            final DeserializationContext ctxt) throws IOException {
        final JsonNode node = jp.getCodec().readTree(jp);

        final TrainCategory trainCategory = new TrainCategory();
        trainCategory.name = node.get("nimi").textValue();
        trainCategory.id = node.get("id").asLong();

        return trainCategory;
    }
}