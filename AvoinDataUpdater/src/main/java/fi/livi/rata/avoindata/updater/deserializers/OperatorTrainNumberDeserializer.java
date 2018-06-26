package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.metadata.OperatorTrainNumber;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OperatorTrainNumberDeserializer extends AEntityDeserializer<OperatorTrainNumber> {
    @Override
    public OperatorTrainNumber deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        OperatorTrainNumber operatorTrainNumber = new OperatorTrainNumber();

        operatorTrainNumber.id = node.get("id").asLong();
        operatorTrainNumber.bottomLimit = node.get("alaraja").asInt();
        operatorTrainNumber.topLimit = node.get("ylaraja").asInt();
        operatorTrainNumber.trainCategory = getStringFromNode(node.get("junalaji"), "nimi");

        return operatorTrainNumber;
    }
}
