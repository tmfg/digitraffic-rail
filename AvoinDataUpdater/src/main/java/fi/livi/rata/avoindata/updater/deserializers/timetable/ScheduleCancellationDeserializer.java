package fi.livi.rata.avoindata.updater.deserializers.timetable;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class ScheduleCancellationDeserializer extends AEntityDeserializer<ScheduleCancellation> {

    @Override
    public ScheduleCancellation deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final ScheduleCancellation scheduleCancellation = new ScheduleCancellation();

        scheduleCancellation.id = node.get("id").asLong();
        scheduleCancellation.startDate = getNodeAsLocalDate(node.get("alkuPvm"));
        scheduleCancellation.endDate = getNodeAsLocalDate(node.get("loppuPvm"));

        final ScheduleCancellation.ScheduleCancellationType cancellationType = parseScheduleCancellationType(node);

        scheduleCancellation.scheduleCancellationType = cancellationType;

        final ArrayList<ScheduleRow> cancelledScheduleRows = Lists.newArrayList(
                jsonParser.getCodec().readValue(node.get("aikataulurivis").traverse(jsonParser.getCodec()), ScheduleRow[].class));

        for (final ScheduleRow cancelledScheduleRow : cancelledScheduleRows) {
            scheduleCancellation.cancelledRows.add(cancelledScheduleRow.departure);
            scheduleCancellation.cancelledRows.add(cancelledScheduleRow.arrival);

            if (cancelledScheduleRow.arrival != null) {
                cancelledScheduleRow.arrival.scheduleRow = cancelledScheduleRow;
            }

            if (cancelledScheduleRow.departure != null) {
                cancelledScheduleRow.departure.scheduleRow = cancelledScheduleRow;
            }
        }



        return scheduleCancellation;
    }

    private ScheduleCancellation.ScheduleCancellationType parseScheduleCancellationType(final JsonNode node) {
        final String peruType = node.get("peruType").asText();
        if (peruType.equals("KOPE")) {
            return ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY;
        } else if (peruType.equals("OVPE")) {
            return ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        } else if (peruType.equals("KVPE")) {
            return ScheduleCancellation.ScheduleCancellationType.DIFFERENT_ROUTE;
        } else {
            throw new IllegalArgumentException("Unknown cancellation type:" + peruType);
        }
    }
}