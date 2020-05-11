package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;

@Component
public class WagonDeserializer extends AEntityDeserializer<Wagon> {
    @Value("#{'${updater.typesForVehicleNumberPublishinIsAllowed}'.split(',')}")
    private Set<String> typesForVehicleNumberPublishinIsAllowed;

    @Override
    public Wagon deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        Wagon wagon = new Wagon();
        wagon.location = node.get("sijainti").asInt();
        wagon.salesNumber = node.get("myyntinumero").asInt();
        wagon.length = node.get("pituus").asInt();
        wagon.playground = nullIfFalse(node.get("leikkitila"));
        wagon.pet = nullIfFalse(node.get("lemmikkielainosasto"));
        wagon.catering = nullIfFalse(node.get("kahvio"));
        wagon.video = nullIfFalse(node.get("video"));
        wagon.luggage = nullIfFalse(node.get("tilaaMatkalaukuille"));
        wagon.smoking = nullIfFalse(node.get("tupakointi"));
        wagon.disabled = nullIfFalse(node.get("pyoratuolipaikka"));

        final JsonNode kalustoyksikko = node.get("kalustoyksikko");
        if (!isNodeNull(kalustoyksikko)) {
            String sarjatunnus = kalustoyksikko.get("sarjatunnus").asText();
            wagon.wagonType = sarjatunnus;

            JsonNode kalustoyksikkonroNode = kalustoyksikko.get("kalustoyksikkonro");
            if (!isNodeNull(kalustoyksikkonroNode) && typesForVehicleNumberPublishinIsAllowed.contains(sarjatunnus)) {
                wagon.vehicleNumber = kalustoyksikkonroNode.asText();
            }
        }
        return wagon;
    }


}
