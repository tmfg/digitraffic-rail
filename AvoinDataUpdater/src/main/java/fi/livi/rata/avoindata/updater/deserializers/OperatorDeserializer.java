package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.metadata.Operator;
import fi.livi.rata.avoindata.common.domain.metadata.OperatorTrainNumber;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class OperatorDeserializer extends AEntityDeserializer<Operator> {

    @Override
    public Operator deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final Operator operator = new Operator();

        operator.id = node.get("id").asLong();
        operator.operatorName = node.get("nimi").asText();
        operator.operatorShortCode = node.get("lyhenne").asText();
        operator.operatorUICCode = node.get("uicKoodi").asInt();

        final JsonNode junanumerosarjat = node.get("junanumerosarjat");
        if (junanumerosarjat != null) {
            final Set<OperatorTrainNumber> operatorTrainNumbers = new HashSet<OperatorTrainNumber>(Arrays.asList(
                    jsonParser.getCodec().readValue(junanumerosarjat.traverse(jsonParser.getCodec()), OperatorTrainNumber[].class)));
            operator.trainNumbers = operatorTrainNumbers;

            for (final OperatorTrainNumber operatorTrainNumber : operatorTrainNumbers) {
                operatorTrainNumber.operator = operator;
            }
        }


        return operator;
    }
}
