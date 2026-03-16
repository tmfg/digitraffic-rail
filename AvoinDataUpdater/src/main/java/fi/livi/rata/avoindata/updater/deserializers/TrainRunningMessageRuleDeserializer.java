package fi.livi.rata.avoindata.updater.deserializers;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageRule;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageTypeEnum;
import org.springframework.stereotype.Component;


@Component
public class TrainRunningMessageRuleDeserializer extends AEntityDeserializer<TrainRunningMessageRule> {

    @Override
    public TrainRunningMessageRule deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) {
        final JsonNode node = jsonParser.readValueAsTree();

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
