package fi.livi.rata.avoindata.updater.deserializers.timetable;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriodChangeDate;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import org.springframework.stereotype.Component;


@Component
public class TimeTablePeriodChangeDateDeserializer extends AEntityDeserializer<TimeTablePeriodChangeDate> {
    @Override
    public TimeTablePeriodChangeDate deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) {
        final JsonNode node = jsonParser.readValueAsTree();

        final TimeTablePeriodChangeDate timeTablePeriodChangeDate = new TimeTablePeriodChangeDate();

        timeTablePeriodChangeDate.id = node.get("id").asLong();
        timeTablePeriodChangeDate.capacityRequestSubmissionDeadline = getNodeAsLocalDate(node.get("hakuLoppupvm"));
        timeTablePeriodChangeDate.effectiveFrom = getNodeAsLocalDate(node.get("voimaantuloPvm"));

        return timeTablePeriodChangeDate;
    }
}
