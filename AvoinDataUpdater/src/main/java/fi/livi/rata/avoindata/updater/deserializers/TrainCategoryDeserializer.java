package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import org.springframework.stereotype.Component;

@Component
public class TrainCategoryDeserializer extends ValueDeserializer<TrainCategory> {
    @Override
    public TrainCategory deserialize(final JsonParser jp,
            final DeserializationContext ctxt) {
        final JsonNode node = jp.readValueAsTree();

        final TrainCategory trainCategory = new TrainCategory();
        trainCategory.name = node.get("nimi").textValue();
        trainCategory.id = node.get("id").asLong();

        return trainCategory;
    }
}