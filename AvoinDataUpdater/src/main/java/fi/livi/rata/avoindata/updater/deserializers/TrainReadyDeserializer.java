package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.train.TrainReady;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZonedDateTime;

@Component
public class TrainReadyDeserializer {
    private Logger logger = LoggerFactory.getLogger(TrainReadyDeserializer.class);

    public TrainReady deserialize(JsonNode node) throws IOException {
        if (node.get("lviTila") == null || node.get("lviLahde") == null) {
            return null;
        }

        TrainReady trainReady = new TrainReady();
        trainReady.source = parseTrainReadyStateEnum(node.get("lviLahde"));
        trainReady.accepted = node.get("lviTila").asText().equals("LUPA_ANNETTU");
        trainReady.timestamp = getNodeAsDateTime(node.get("lviTilanMuokkausaika"));

        return trainReady;
    }

    private TrainReady.TrainReadySource parseTrainReadyStateEnum(final JsonNode source) {
        final String trainReadySource = source.textValue();

        if (trainReadySource.equals("soittopalvelin")) {
            return TrainReady.TrainReadySource.PHONE;
        } else if (trainReadySource.equals("LIIKE")) {
            return TrainReady.TrainReadySource.LIIKE;
        } else if (trainReadySource.equals("KUPLA")) {
            return TrainReady.TrainReadySource.KUPLA;
        } else {
            logger.error("Unknown TrainReadyState {}", source);
            return TrainReady.TrainReadySource.UNKNOWN;
        }
    }

    protected ZonedDateTime getNodeAsDateTime(final JsonNode node) {
        if (node == null) {
            return null;
        }
        return ZonedDateTime.parse(node.asText());
    }
}
