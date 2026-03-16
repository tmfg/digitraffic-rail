package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.metadata.OperatorTrainNumber;
import org.springframework.stereotype.Component;


@Component
public class OperatorTrainNumberDeserializer extends AEntityDeserializer<OperatorTrainNumber> {
    @Override
    public OperatorTrainNumber deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) {
        final JsonNode node = jsonParser.readValueAsTree();

        final OperatorTrainNumber operatorTrainNumber = new OperatorTrainNumber();

        operatorTrainNumber.id = node.get("id").asLong();
        operatorTrainNumber.bottomLimit = node.get("alaraja").asInt();
        operatorTrainNumber.topLimit = node.get("ylaraja").asInt();
        operatorTrainNumber.trainCategory = getStringFromNode(node.get("junalaji"), "nimi");

        return operatorTrainNumber;
    }
}
