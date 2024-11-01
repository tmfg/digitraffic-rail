package fi.livi.rata.avoindata.updater.deserializers.timetable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriodChangeDate;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TimeTablePeriodChangeDateDeserializer extends AEntityDeserializer<TimeTablePeriodChangeDate> {
    @Override
    public TimeTablePeriodChangeDate deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final TimeTablePeriodChangeDate timeTablePeriodChangeDate = new TimeTablePeriodChangeDate();

        timeTablePeriodChangeDate.id = node.get("id").asLong();
        timeTablePeriodChangeDate.capacityRequestSubmissionDeadline = getNodeAsLocalDate(node.get("hakuLoppupvm"));
        timeTablePeriodChangeDate.effectiveFrom = getNodeAsLocalDate(node.get("voimaantuloPvm"));

        return timeTablePeriodChangeDate;
    }
}
