package fi.livi.rata.avoindata.updater.deserializers.timetable;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleException;
import org.springframework.stereotype.Component;


@Component
public class ScheduleExceptionDeserializer extends AEntityDeserializer<ScheduleException> {

    @Override
    public ScheduleException deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) {
        final JsonNode node = jsonParser.readValueAsTree();

        final ScheduleException scheduleException = new ScheduleException();
        scheduleException.id = node.get("id").asLong();
        scheduleException.isRun = node.get("ajetaan").asBoolean();
        scheduleException.date = getNodeAsLocalDate(node.get("pvm"));

        return scheduleException;
    }
}
