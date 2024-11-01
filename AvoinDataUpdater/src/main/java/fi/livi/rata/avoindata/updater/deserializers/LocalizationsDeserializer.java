package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.localization.Localizations;
import fi.livi.rata.avoindata.common.domain.localization.PowerType;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LocalizationsDeserializer extends AEntityDeserializer<Localizations> {
    @Override
    public Localizations deserialize(final JsonParser jsonParser,
            final DeserializationContext ctxt) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final Localizations localizations = new Localizations();
        localizations.powerTypes = getObjectsFromNode(jsonParser, node, PowerType[].class, "vetovoimalajis");
        localizations.trainTypes = getObjectsFromNode(jsonParser, node, TrainType[].class, "junatyyppis");

        return localizations;
    }


}
