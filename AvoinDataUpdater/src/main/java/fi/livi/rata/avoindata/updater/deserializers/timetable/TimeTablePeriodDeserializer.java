package fi.livi.rata.avoindata.updater.deserializers.timetable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriodChangeDate;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TimeTablePeriodDeserializer extends AEntityDeserializer<TimeTablePeriod> {
    @Override
    public TimeTablePeriod deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        TimeTablePeriod timeTablePeriod = new TimeTablePeriod();

        timeTablePeriod.id = node.get("id").asLong();
        timeTablePeriod.name = node.get("nimi").textValue();

        timeTablePeriod.effectiveFrom = getNodeAsLocalDate(node.get("voimassaAlkuPvm"));
        timeTablePeriod.effectiveTo = getNodeAsLocalDate(node.get("voimassaAlkuPvm"));
        timeTablePeriod.capacityRequestSubmissionDeadline = getNodeAsLocalDate(node.get("hakuLoppupvm"));
        timeTablePeriod.capacityAllocationConfirmDate = getNodeAsLocalDate(node.get("jakopaatosViimeistaanPvm"));

        timeTablePeriod.changeDates = Lists.newArrayList(jsonParser.getCodec().readValue(node.get("muutosajankohdat").traverse(jsonParser.getCodec()), TimeTablePeriodChangeDate[].class));

        for (TimeTablePeriodChangeDate changeDate : timeTablePeriod.changeDates) {
            changeDate.timeTablePeriod = timeTablePeriod;
        }

        return timeTablePeriod;
    }
}
