package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import org.springframework.stereotype.Component;


@Component
public class TrainTypeDeserializer extends AEntityDeserializer<TrainType> {
    @Override
    public TrainType deserialize(final JsonParser jp,
            final DeserializationContext ctxt) {
        final JsonNode node = jp.readValueAsTree();

        final TrainType trainType = new TrainType();
        trainType.name = node.get("nimi").textValue();
        trainType.id = node.get("id").asLong();

        final TrainCategory trainCategory = getObjectFromNode(jp, node, "junalaji", TrainCategory.class);
        trainType.trainCategory = trainCategory;

        return trainType;
    }

}