package fi.livi.rata.avoindata.updater.deserializers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;

import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;

@Component
public class SimpleTimeTableRowDeserializer extends AEntityDeserializer<SimpleTimeTableRow> {

    @Override
    public SimpleTimeTableRow deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
        final JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        final long attapId = node.get("attap_id").asLong();
        final LocalDate departureDate = LocalDate.parse(node.get("departure_date").asText(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        final long trainNumber = node.get("train_number").asLong();

        final String commercialTrack = node.get("commercial_track").asText();
        final ZonedDateTime scheduledTime = ZonedDateTime.parse(node.get("scheduled_time").asText(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(
                ZoneId.of("UTC")));
        final String stationShortCode = node.get("station_short_code").asText();
        final TimeTableRow.TimeTableRowType type = "0".equals(
                node.get("type").asText()) ? TimeTableRow.TimeTableRowType.ARRIVAL : TimeTableRow.TimeTableRowType.DEPARTURE;

        return new SimpleTimeTableRow(attapId, departureDate, trainNumber, commercialTrack, scheduledTime, stationShortCode, type);
    }

}
