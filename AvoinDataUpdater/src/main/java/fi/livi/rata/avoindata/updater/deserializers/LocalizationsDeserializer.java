package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.localization.Localizations;
import fi.livi.rata.avoindata.common.domain.localization.PowerType;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import org.springframework.stereotype.Component;


@Component
public class LocalizationsDeserializer extends AEntityDeserializer<Localizations> {
    @Override
    public Localizations deserialize(final JsonParser jsonParser,
            final DeserializationContext ctxt) {
        final JsonNode node = jsonParser.readValueAsTree();

        final Localizations localizations = new Localizations();
        localizations.powerTypes = getObjectsFromNode(jsonParser, node, PowerType[].class, "vetovoimalajis");
        localizations.trainTypes = getObjectsFromNode(jsonParser, node, TrainType[].class, "junatyyppis");

        return localizations;
    }


}
