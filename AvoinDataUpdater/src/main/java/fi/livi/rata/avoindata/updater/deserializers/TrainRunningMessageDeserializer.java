package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageTypeEnum;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;

@Component
public class TrainRunningMessageDeserializer extends AEntityDeserializer<TrainRunningMessage> {
    @Override
    public TrainRunningMessage deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final TrainRunningMessage trainRunningMessage = new TrainRunningMessage();
        trainRunningMessage.timestamp = getNodeAsDateTime(node.get("tapahtumaPvm"));

        final JsonNode id = node.get("id");
        trainRunningMessage.id = id.asLong();

        final LocalDate departureDate = getDepartureDate(node);
        final JsonNode junanumero = node.get("junanumero");
        final String trainNumber = junanumero.textValue();
        trainRunningMessage.trainId = new StringTrainId(trainNumber, departureDate);

        trainRunningMessage.version = node.get("version").asLong();

        trainRunningMessage.trackSection = getStringFromNode(node, "raideosuus");
        trainRunningMessage.nextTrackSection = getNullableString(node, "raideosuusSeuraava");
        trainRunningMessage.previousTrackSection = getNullableString(node, "raideosuusEdellinen");

        trainRunningMessage.station = getStationString(node, "liikennepaikka");
        trainRunningMessage.nextStation = getStationString(node, "liikennepaikkaSeuraava");
        trainRunningMessage.previousStation = getStationString(node, "liikennepaikkaEdellinen");

        setType(node, trainRunningMessage);

        return trainRunningMessage;
    }

    private String getStationString(final JsonNode node, final String fieldName) {
        final String liikennepaikka = getNullableString(node, fieldName);

        if (liikennepaikka == null) {
            return null;
        } else if (liikennepaikka.equals("ALKU")) {
            return "START";
        } else {
            return liikennepaikka;
        }
    }

    private void setType(final JsonNode node, final TrainRunningMessage trainRunningMessage) {
        final String raideosuudenVarautuminen = node.get("raideosuudenVarautuminen").asText();
        if (raideosuudenVarautuminen.equals("O")) {
            trainRunningMessage.type = TrainRunningMessageTypeEnum.OCCUPY;
        } else if (raideosuudenVarautuminen.equals("R")) {
            trainRunningMessage.type = TrainRunningMessageTypeEnum.RELEASE;
        } else {
            throw new IllegalArgumentException("Unknown type:" + raideosuudenVarautuminen);
        }
    }

    private LocalDate getDepartureDate(final JsonNode node) {
        final JsonNode lahtopvm = node.get("lahtopvm");
        if (lahtopvm != null && !Strings.isNullOrEmpty(lahtopvm.textValue())) {
            final LocalDate departureDate = getNodeAsLocalDate(lahtopvm);
            return departureDate;
        } else {
            return null;
        }
    }
}
