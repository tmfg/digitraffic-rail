package fi.livi.rata.avoindata.updater.deserializers.timetable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TimeTablePeriodDeserializer extends AEntityDeserializer<TimeTablePeriod> {
    @Override
    public TimeTablePeriod deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        TimeTablePeriod timetablePeriod = new TimeTablePeriod();

        timetablePeriod.id = node.get("id").asLong();

        return timetablePeriod;
    }
}
