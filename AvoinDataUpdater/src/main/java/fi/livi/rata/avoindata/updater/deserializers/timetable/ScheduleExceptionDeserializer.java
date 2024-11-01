package fi.livi.rata.avoindata.updater.deserializers.timetable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ScheduleExceptionDeserializer extends AEntityDeserializer<ScheduleException> {

    @Override
    public ScheduleException deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final ScheduleException scheduleException = new ScheduleException();
        scheduleException.id = node.get("id").asLong();
        scheduleException.isRun = node.get("ajetaan").asBoolean();
        scheduleException.date = getNodeAsLocalDate(node.get("pvm"));

        return scheduleException;
    }
}
