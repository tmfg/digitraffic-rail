package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TrainTypeDeserializer extends AEntityDeserializer<TrainType> {
    @Override
    public TrainType deserialize(final JsonParser jp,
            final DeserializationContext ctxt) throws IOException {
        final JsonNode node = jp.getCodec().readTree(jp);

        final TrainType trainType = new TrainType();
        trainType.name = node.get("nimi").textValue();
        trainType.id = node.get("id").asLong();

        final TrainCategory trainCategory = getObjectFromNode(jp, node, "junalaji", TrainCategory.class);
        trainType.trainCategory = trainCategory;

        return trainType;
    }

}