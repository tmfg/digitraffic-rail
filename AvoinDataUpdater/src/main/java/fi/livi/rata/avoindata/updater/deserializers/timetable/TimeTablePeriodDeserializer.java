package fi.livi.rata.avoindata.updater.deserializers.timetable;


import org.springframework.stereotype.Component;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriodChangeDate;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;

@Component
public class TimeTablePeriodDeserializer extends AEntityDeserializer<TimeTablePeriod> {
    @Override
    public TimeTablePeriod deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) {
        final JsonNode node = jsonParser.readValueAsTree();

        final TimeTablePeriod timeTablePeriod = new TimeTablePeriod();

        timeTablePeriod.id = node.get("id").asLong();
        timeTablePeriod.name = node.get("nimi").textValue();

        timeTablePeriod.effectiveFrom = getNodeAsLocalDate(node.get("voimassaAlkuPvm"));
        timeTablePeriod.effectiveTo = getNodeAsLocalDate(node.get("voimassaLoppuPvm"));
        timeTablePeriod.capacityRequestSubmissionDeadline = getNodeAsLocalDate(node.get("hakuLoppupvm"));
        timeTablePeriod.capacityAllocationConfirmDate = getNodeAsLocalDate(node.get("jakopaatosViimeistaanPvm"));

        timeTablePeriod.changeDates = Lists.newArrayList(jsonParser.objectReadContext().readValue(node.get("muutosajankohdat").traverse(jsonParser.objectReadContext()), TimeTablePeriodChangeDate[].class));

        for (final TimeTablePeriodChangeDate changeDate : timeTablePeriod.changeDates) {
            changeDate.timeTablePeriod = timeTablePeriod;
        }

        return timeTablePeriod;
    }
}
