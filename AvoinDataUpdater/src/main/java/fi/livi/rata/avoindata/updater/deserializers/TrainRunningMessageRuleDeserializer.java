package fi.livi.rata.avoindata.updater.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageRule;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageTypeEnum;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TrainRunningMessageRuleDeserializer extends AEntityDeserializer<TrainRunningMessageRule> {

    @Override
    public TrainRunningMessageRule deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final TrainRunningMessageRule entity = new TrainRunningMessageRule();

        entity.id = node.get("id").asLong();
        entity.timeTableRowStationShortCode = node.get("liikennepaikka").asText();
        entity.timeTableRowType = node.get("tyyppi").asText().equals("L") ? TimeTableRow.TimeTableRowType.DEPARTURE : TimeTableRow
                .TimeTableRowType.ARRIVAL;

        entity.trainRunningMessageStationShortCode = node.get("liikennepaikkaHerate").asText();
        entity.trainRunningMessageType = node.get("varautumisenTyyppi").asText().equals("O") ? TrainRunningMessageTypeEnum.OCCUPY : TrainRunningMessageTypeEnum.RELEASE;
        entity.trainRunningMessageNextStationShortCode = node.get("suunnanLiikennepaikka").asText();
        entity.trainRunningMessageTrackSection = node.get("raideosuusHerate").asText();

        entity.offset = node.get("offset").asInt();

        return entity;
    }
}
