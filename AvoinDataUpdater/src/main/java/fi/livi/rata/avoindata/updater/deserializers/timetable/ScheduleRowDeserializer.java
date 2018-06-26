package fi.livi.rata.avoindata.updater.deserializers.timetable;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class ScheduleRowDeserializer extends AEntityDeserializer<ScheduleRow> {

    @Override
    public ScheduleRow deserialize(final JsonParser jsonParser,
            final DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        ScheduleRow scheduleRow = new ScheduleRow();

        scheduleRow.id = node.get("id").asLong();
        final StationEmbeddable stationEmbeddable = new StationEmbeddable();
        stationEmbeddable.stationShortCode = node.get("liikennepaikka").get("lyhenne").asText();
        stationEmbeddable.countryCode = node.get("liikennepaikka").get("maakoodi").asText();
        stationEmbeddable.stationUICCode = node.get("liikennepaikka").get("uicKoodi").asInt();
        scheduleRow.station = stationEmbeddable;

        final Set<ScheduleRowPart> departure = Sets.newHashSet(
                jsonParser.getCodec().readValue(node.get("lahto").traverse(jsonParser.getCodec()), ScheduleRowPart[].class));

        final Set<ScheduleRowPart> arrival = Sets.newHashSet(
                jsonParser.getCodec().readValue(node.get("saapuminen").traverse(jsonParser.getCodec()), ScheduleRowPart[].class));

        scheduleRow.arrival = getFirstOrNull(arrival);
        scheduleRow.departure = getFirstOrNull(departure);

        final JsonNode lpRaideNode = node.get("liikennepaikanRaide");
        if (lpRaideNode != null) {
            scheduleRow.commercialTrack = lpRaideNode.get("kaupallinenNro").asText();
        }

        if (scheduleRow.arrival != null) {
            scheduleRow.arrival.scheduleRow = scheduleRow;
        }

        if (scheduleRow.departure != null) {
            scheduleRow.departure.scheduleRow = scheduleRow;
        }

        return scheduleRow;
    }

    private ScheduleRowPart getFirstOrNull(final Set<ScheduleRowPart> departure) {
        if (departure == null || departure.isEmpty()) {
            return null;
        } else {
            return departure.iterator().next();
        }
    }
}