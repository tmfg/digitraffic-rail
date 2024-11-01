package fi.livi.rata.avoindata.updater.deserializers.timetable;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriodChangeDate;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;

@Component
public class TimeTablePeriodDeserializer extends AEntityDeserializer<TimeTablePeriod> {
    @Override
    public TimeTablePeriod deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final TimeTablePeriod timeTablePeriod = new TimeTablePeriod();

        timeTablePeriod.id = node.get("id").asLong();
        timeTablePeriod.name = node.get("nimi").textValue();

        timeTablePeriod.effectiveFrom = getNodeAsLocalDate(node.get("voimassaAlkuPvm"));
        timeTablePeriod.effectiveTo = getNodeAsLocalDate(node.get("voimassaLoppuPvm"));
        timeTablePeriod.capacityRequestSubmissionDeadline = getNodeAsLocalDate(node.get("hakuLoppupvm"));
        timeTablePeriod.capacityAllocationConfirmDate = getNodeAsLocalDate(node.get("jakopaatosViimeistaanPvm"));

        timeTablePeriod.changeDates = Lists.newArrayList(jsonParser.getCodec().readValue(node.get("muutosajankohdat").traverse(jsonParser.getCodec()), TimeTablePeriodChangeDate[].class));

        for (final TimeTablePeriodChangeDate changeDate : timeTablePeriod.changeDates) {
            changeDate.timeTablePeriod = timeTablePeriod;
        }

        return timeTablePeriod;
    }
}
