package fi.livi.rata.avoindata.updater.deserializers.timetable;


import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class ScheduleCancellationDeserializer extends AEntityDeserializer<ScheduleCancellation> {

    @Override
    public ScheduleCancellation deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) {
        final JsonNode node = jsonParser.readValueAsTree();

        final ScheduleCancellation scheduleCancellation = new ScheduleCancellation();

        scheduleCancellation.id = node.get("id").asLong();
        scheduleCancellation.startDate = getNodeAsLocalDate(node.get("alkuPvm"));
        scheduleCancellation.endDate = getNodeAsLocalDate(node.get("loppuPvm"));

        final ScheduleCancellation.ScheduleCancellationType cancellationType = parseScheduleCancellationType(node);

        scheduleCancellation.scheduleCancellationType = cancellationType;

        final ArrayList<ScheduleRow> cancelledScheduleRows = Lists.newArrayList(
                jsonParser.objectReadContext().readValue(node.get("aikataulurivis").traverse(jsonParser.objectReadContext()), ScheduleRow[].class));

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