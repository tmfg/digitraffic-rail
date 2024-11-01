package fi.livi.rata.avoindata.updater.deserializers.timetable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class ScheduleRowPartDeserializer extends AEntityDeserializer<ScheduleRowPart> {

    @Override
    public ScheduleRowPart deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final ScheduleRowPart scheduleRowPart = new ScheduleRowPart();

        scheduleRowPart.id = node.get("id").asLong();
        final JsonNode pysahdystyyppiNode = node.get("pysahdystyyppi");
        if (pysahdystyyppiNode != null) {
            scheduleRowPart.stopType = getStoptype(pysahdystyyppiNode);
        }

        scheduleRowPart.timestamp = Duration.between(LocalDateTime.of(1970, 1, 1, 0, 0, 0),
                getNodeAsLocalDateTime(node.get("tapahtumaAika")));

        return scheduleRowPart;
    }


    private ScheduleRow.ScheduleRowStopType getStoptype(final JsonNode node) {
        final long stoptypeId = node.get("id").asLong();
        if (stoptypeId == 1) {
            return ScheduleRow.ScheduleRowStopType.PASS;
        } else if (stoptypeId == 2) {
            return ScheduleRow.ScheduleRowStopType.COMMERCIAL;
        } else if (stoptypeId == 3) {
            return ScheduleRow.ScheduleRowStopType.NONCOMMERCIAL;
        } else {
            throw new IllegalArgumentException("Unknown stoptype " + stoptypeId);
        }
    }
}
